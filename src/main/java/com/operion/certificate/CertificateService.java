package com.operion.certificate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.document.DocumentBranding;
import com.operion.document.DocumentBrandingService;
import com.operion.document.IssuedDocument;
import com.operion.document.IssuedDocumentKind;
import com.operion.document.IssuedDocumentRepository;
import com.operion.document.IssuedDocumentStatus;
import com.operion.document.pdf.DateText;
import com.operion.document.pdf.DocumentHtmlRenderer;
import com.operion.document.pdf.MoneyText;
import com.operion.document.pdf.PageFormat;
import com.operion.document.pdf.PdfRenderer;
import com.operion.document.pdf.QrCodes;
import com.operion.organisation.DocumentNumberFormatter;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Certificate requests from raise to print. Raise checks the type's rules (exit recorded, once per student, no
 * duplicate open request); approval, when the type needs it, must come from someone other than the requester;
 * issue checks dues clearance (overridable with a recorded reason), takes the next number from the type's own
 * counter, and freezes everything printed into the issued-documents register. Every later print re-renders that
 * snapshot - office reprints after the first are marked DUPLICATE, guardian downloads are not counted, and a
 * cancelled certificate still renders, marked CANCELLED, so the public verification page and the paper agree.
 */
@Service
public class CertificateService {

	public static final String SUBJECT_TYPE = "CERTIFICATE_REQUEST";

	private static final EnumSet<CertificateRequestStatus> OPEN_OR_ISSUED = EnumSet.of(CertificateRequestStatus.PENDING_APPROVAL,
			CertificateRequestStatus.APPROVED, CertificateRequestStatus.ISSUED);
	private static final EnumSet<CertificateRequestStatus> OPEN = EnumSet.of(CertificateRequestStatus.PENDING_APPROVAL,
			CertificateRequestStatus.APPROVED);
	private static final int MAX_MANUAL_VALUE_LENGTH = 500;
	private static final SecureRandom RANDOM = new SecureRandom();

	public record ClearanceItem(String code, String label, boolean clear, String detail) {
	}

	public record Clearance(boolean clear, List<ClearanceItem> items) {
	}

	public record Particular(String label, String value) {
	}

	/** Everything printed, frozen at issue. */
	public record CertificateSnapshot(String certificateNumber, String typeCode, String typeName, String title, List<String> paragraphs,
			List<Particular> particulars, String studentName, String admissionNumber, LocalDate issueDate, String issueDateText,
			String verificationToken, DocumentBranding school, Instant issuedAt) {
	}

	public record PreparedCertificate(CertificateSnapshot snapshot, boolean duplicate, int printNumber, boolean cancelled,
			PageFormat pageFormat, String verifyUrl) {
	}

	/** What the public verification page may show - deliberately minimal. */
	public record VerificationResult(String documentKind, String certificateName, String certificateNumber, LocalDate issueDate,
			String schoolName, String studentName, String status) {
	}

	private final CertificateRequestRepository requestRepository;
	private final CertificateTypeRepository typeRepository;
	private final StudentRepository studentRepository;
	private final StudentExitRepository exitRepository;
	private final IssuedDocumentRepository issuedDocumentRepository;
	private final OrganisationRepository organisationRepository;
	private final CertificateMergeFields mergeFields;
	private final DocumentBrandingService brandingService;
	private final DocumentHtmlRenderer htmlRenderer;
	private final PdfRenderer pdfRenderer;
	private final ObjectMapper objectMapper;
	private final String frontendBaseUrl;

	public CertificateService(CertificateRequestRepository requestRepository, CertificateTypeRepository typeRepository,
			StudentRepository studentRepository, StudentExitRepository exitRepository, IssuedDocumentRepository issuedDocumentRepository,
			OrganisationRepository organisationRepository, CertificateMergeFields mergeFields, DocumentBrandingService brandingService,
			DocumentHtmlRenderer htmlRenderer, PdfRenderer pdfRenderer, ObjectMapper objectMapper,
			@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
		this.requestRepository = requestRepository;
		this.typeRepository = typeRepository;
		this.studentRepository = studentRepository;
		this.exitRepository = exitRepository;
		this.issuedDocumentRepository = issuedDocumentRepository;
		this.organisationRepository = organisationRepository;
		this.mergeFields = mergeFields;
		this.brandingService = brandingService;
		this.htmlRenderer = htmlRenderer;
		this.pdfRenderer = pdfRenderer;
		this.objectMapper = objectMapper;
		this.frontendBaseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
	}

	public List<CertificateRequest> list(CertificateRequestStatus status, Long studentId) {
		if (studentId != null) {
			return requestRepository.findByStudentIdOrderByIdDesc(studentId).stream()
					.filter(request -> status == null || request.getStatus() == status).toList();
		}
		return status == null ? requestRepository.findAllByOrderByIdDesc() : requestRepository.findByStatusOrderByIdDesc(status);
	}

	public CertificateRequest get(Long requestId) {
		return requestRepository.findById(requestId).orElseThrow(() -> new IllegalArgumentException("No certificate request with id " + requestId));
	}

	@Transactional
	public CertificateRequest request(Long studentId, Long typeId, String purpose, Map<String, String> manualValues,
			CertificateRequestChannel channel) {
		CertificateType type = typeRepository.findById(typeId).orElseThrow(() -> new IllegalArgumentException("No certificate type with id " + typeId));
		Student student = studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		if (!type.isActive()) {
			throw new InvalidRequestException(type.getName() + " is switched off for this school");
		}
		if (channel == CertificateRequestChannel.PORTAL && !type.isGuardianRequestable()) {
			throw new AuthorizationDeniedException(type.getName() + " cannot be requested from the portal");
		}
		if (type.isRequiresExit() && exitRepository.findByStudentId(studentId).isEmpty()) {
			throw new InvalidRequestException(type.getName() + " needs the student's exit to be recorded first");
		}
		if (type.isOncePerStudent() && requestRepository.existsByStudentIdAndCertificateTypeIdAndStatusIn(studentId, typeId, OPEN_OR_ISSUED)) {
			throw new IllegalStateException(type.getName() + " has already been requested or issued for this student - cancel the existing one first");
		}
		if (requestRepository.existsByStudentIdAndCertificateTypeIdAndStatusIn(studentId, typeId, OPEN)) {
			throw new IllegalStateException("There is already an open " + type.getName() + " request for this student");
		}
		return requestRepository.save(new CertificateRequest(type, student, trimToNull(purpose), writeManual(type, manualValues), channel));
	}

	@Transactional
	public CertificateRequest updateDetails(Long requestId, String purpose, Map<String, String> manualValues) {
		CertificateRequest request = get(requestId);
		request.updateManualValues(trimToNull(purpose), writeManual(request.getCertificateType(), manualValues));
		return request;
	}

	@Transactional
	public CertificateRequest approve(Long requestId) {
		CertificateRequest request = get(requestId);
		Long actor = TenantContext.getActorId();
		if (actor != null && actor.equals(request.getCreatedBy())) {
			throw new AuthorizationDeniedException("A certificate request must be approved by someone other than the person who raised it");
		}
		request.approve(actor);
		return request;
	}

	@Transactional
	public CertificateRequest reject(Long requestId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new InvalidRequestException("Give a reason for rejecting the request");
		}
		CertificateRequest request = get(requestId);
		request.reject(TenantContext.getActorId(), reason.trim());
		return request;
	}

	/** Dues that block a certificate whose type requires clearance. Live, not stored - it changes as dues are settled. */
	public Clearance clearance(Student student) {
		List<ClearanceItem> items = new ArrayList<>();
		String currency = brandingService.current().currencyCode();
		BigDecimal fees = requestRepository.outstandingFees(student.getId());
		items.add(new ClearanceItem("FEES", "Fees", fees.signum() <= 0, fees.signum() <= 0 ? "No dues" : MoneyText.format(fees, currency) + " outstanding"));
		Long personId = student.getPerson().getId();
		long loans = requestRepository.openLibraryLoans(personId);
		items.add(new ClearanceItem("LIBRARY_LOANS", "Library books", loans == 0, loans == 0 ? "All returned" : loans + " not returned"));
		BigDecimal fines = requestRepository.pendingLibraryFines(personId);
		items.add(new ClearanceItem("LIBRARY_FINES", "Library fines", fines.signum() <= 0,
				fines.signum() <= 0 ? "None pending" : MoneyText.format(fines, currency) + " pending"));
		long transport = requestRepository.activeTransportAssignments(student.getId());
		items.add(new ClearanceItem("TRANSPORT", "Transport", transport == 0, transport == 0 ? "Not assigned" : "Still assigned to a route"));
		return new Clearance(items.stream().allMatch(ClearanceItem::clear), items);
	}

	public Clearance clearance(Long requestId) {
		return clearance(get(requestId).getStudent());
	}

	/**
	 * Issues an approved request. If the type requires clearance and dues remain, an override reason is required and
	 * recorded with who gave it; without one the issue is refused listing what is outstanding.
	 */
	@Transactional
	public CertificateRequest issue(Long requestId, String clearanceOverrideReason) {
		CertificateRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new IllegalArgumentException("No certificate request with id " + requestId));
		if (request.getStatus() != CertificateRequestStatus.APPROVED) {
			throw new IllegalStateException("Only an approved request can be issued (this one is " + request.getStatus() + ")");
		}
		Long actor = TenantContext.getActorId();
		CertificateType type = typeRepository.findByIdForUpdate(request.getCertificateType().getId()).orElseThrow();
		if (type.isRequiresClearance()) {
			Clearance clearance = clearance(request.getStudent());
			if (!clearance.clear()) {
				if (clearanceOverrideReason == null || clearanceOverrideReason.isBlank()) {
					throw new IllegalStateException("Dues are not cleared: " + String.join("; ", clearance.items().stream()
							.filter(item -> !item.clear()).map(item -> item.label() + " - " + item.detail()).toList())
							+ ". Settle them, or issue with an override reason.");
				}
				request.overrideClearance(actor, clearanceOverrideReason.trim());
			}
		}

		DocumentBranding school = brandingService.current();
		Map<String, String> firstPass = mergeFields.values(request.getStudent(), school, request.getPurpose(), readManual(request), null, null);
		String number = DocumentNumberFormatter.format(type.getNumberFormat(), type.takeSequence(), firstPass.get("academicYear"),
				LocalDate.now().getYear());
		typeRepository.save(type);

		LocalDate issueDate = LocalDate.now();
		String token = newToken();
		Map<String, String> values = mergeFields.values(request.getStudent(), school, request.getPurpose(), readManual(request), number, issueDate);
		CertificateSnapshot snapshot = new CertificateSnapshot(number, type.getCode(), type.getName(),
				CertificateMergeFields.substitute(type.getTitle(), values), paragraphs(type.getBodyTemplate(), values),
				particulars(type.getParticularsTemplate(), values), values.get("student.name"), request.getStudent().getAdmissionNumber(),
				issueDate, DateText.format(issueDate), token, school, Instant.now());
		String json = objectMapper.writeValueAsString(snapshot);
		IssuedDocument document = issuedDocumentRepository.save(new IssuedDocument(IssuedDocumentKind.CERTIFICATE, number, SUBJECT_TYPE,
				request.getId(), json, sha256(json)));
		request.markIssued(document, token, actor);
		return request;
	}

	@Transactional
	public CertificateRequest cancel(Long requestId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new InvalidRequestException("Give a reason for cancelling the certificate");
		}
		CertificateRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new IllegalArgumentException("No certificate request with id " + requestId));
		request.cancel(reason.trim());
		issuedDocumentRepository.save(request.getIssuedDocument());
		return request;
	}

	/** @param officePrint true for a staff print (counted; second and later are DUPLICATE), false for a guardian's own download. */
	@Transactional
	public PreparedCertificate prepare(Long requestId, boolean officePrint) {
		CertificateRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new IllegalArgumentException("No certificate request with id " + requestId));
		IssuedDocument document = request.getIssuedDocument();
		if (document == null) {
			throw new IllegalStateException("This certificate has not been issued yet");
		}
		if (officePrint) {
			document.recordPrint(TenantContext.getActorId());
			issuedDocumentRepository.save(document);
		}
		CertificateSnapshot snapshot = objectMapper.readValue(document.getDataSnapshot(), CertificateSnapshot.class);
		return new PreparedCertificate(snapshot, officePrint && document.getPrintCount() > 1, document.getPrintCount(),
				document.getStatus() == IssuedDocumentStatus.CANCELLED, PageFormat.fromCode(request.getCertificateType().getPageSize()),
				verifyUrl(snapshot.verificationToken()));
	}

	public byte[] renderPdf(PreparedCertificate certificate) {
		DocumentBranding school = certificate.snapshot().school();
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("c", certificate);
		variables.put("s", certificate.snapshot());
		variables.put("page", certificate.pageFormat());
		variables.put("logo", brandingService.imageDataUri(school.logoRef()).orElse(null));
		variables.put("stamp", brandingService.imageDataUri(school.stampRef()).orElse(null));
		variables.put("signature", brandingService.imageDataUri(school.signatureRef()).orElse(null));
		variables.put("qr", QrCodes.pngDataUri(certificate.verifyUrl()));
		return pdfRenderer.render(htmlRenderer.render("certificate", variables));
	}

	/** Caller must have set TenantContext to the organisation named in the link. */
	public Optional<VerificationResult> verify(String token) {
		if (token == null || token.length() > 40) {
			return Optional.empty();
		}
		return requestRepository.findByVerificationToken(token).filter(request -> request.getIssuedDocument() != null).map(request -> {
			CertificateSnapshot snapshot = objectMapper.readValue(request.getIssuedDocument().getDataSnapshot(), CertificateSnapshot.class);
			return new VerificationResult("CERTIFICATE", snapshot.typeName(), snapshot.certificateNumber(), snapshot.issueDate(),
					snapshot.school().schoolName(), snapshot.studentName(), request.getIssuedDocument().getStatus().name());
		});
	}

	public Map<String, String> readManual(CertificateRequest request) {
		if (request.getManualValues() == null) {
			return Map.of();
		}
		return objectMapper.readValue(request.getManualValues(), new TypeReference<LinkedHashMap<String, String>>() {
		});
	}

	private String verifyUrl(String token) {
		String slug = organisationRepository.findById(TenantContext.getOrganisationId()).map(org -> org.getSlug()).orElse("unknown");
		return frontendBaseUrl + "/verify/" + slug + "/" + token;
	}

	/** Keeps only the type's declared manual keys, trimmed and length-capped; null when nothing is left. */
	private String writeManual(CertificateType type, Map<String, String> manualValues) {
		if (manualValues == null || manualValues.isEmpty()) {
			return null;
		}
		Map<String, String> kept = new LinkedHashMap<>();
		for (CertificateType.ManualField field : type.manualFieldList()) {
			String value = manualValues.get(field.key());
			if (value != null && !value.isBlank()) {
				String trimmed = value.trim();
				if (trimmed.length() > MAX_MANUAL_VALUE_LENGTH) {
					throw new InvalidRequestException(field.label() + " is longer than " + MAX_MANUAL_VALUE_LENGTH + " characters");
				}
				kept.put(field.key(), trimmed);
			}
		}
		return kept.isEmpty() ? null : objectMapper.writeValueAsString(kept);
	}

	static List<String> paragraphs(String bodyTemplate, Map<String, String> values) {
		List<String> paragraphs = new ArrayList<>();
		for (String block : CertificateMergeFields.substitute(bodyTemplate, values).split("\\R\\s*\\R")) {
			String text = block.strip().replaceAll("\\s*\\R\\s*", " ");
			if (!text.isEmpty()) {
				paragraphs.add(text);
			}
		}
		return paragraphs;
	}

	static List<Particular> particulars(String particularsTemplate, Map<String, String> values) {
		List<Particular> rows = new ArrayList<>();
		if (particularsTemplate == null) {
			return rows;
		}
		for (String line : particularsTemplate.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}
			int bar = line.indexOf('|');
			String label = (bar < 0 ? line : line.substring(0, bar)).trim();
			String value = bar < 0 ? "" : CertificateMergeFields.substitute(line.substring(bar + 1).trim(), values);
			rows.add(new Particular(label, value.isEmpty() ? CertificateMergeFields.BLANK : value));
		}
		return rows;
	}

	private static String newToken() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
