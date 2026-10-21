package com.operion.finance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.document.DocumentBranding;
import com.operion.document.DocumentBrandingService;
import com.operion.document.DocumentTemplate;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.DocumentType;
import com.operion.document.IssuedDocument;
import com.operion.document.IssuedDocumentKind;
import com.operion.document.IssuedDocumentRepository;
import com.operion.document.TemplateStyle;
import com.operion.document.pdf.DocumentHtmlRenderer;
import com.operion.document.pdf.MoneyText;
import com.operion.document.pdf.PageFormat;
import com.operion.document.pdf.PdfRenderer;
import com.operion.identity.Person;
import com.operion.student.StudentEnrollment;

import tools.jackson.databind.ObjectMapper;

/**
 * Fee receipts as PDFs. The first print freezes a {@link ReceiptSnapshot} into the
 * issued-document register; every later print re-renders that snapshot, so a receipt
 * never changes after it was handed over. Office reprints after the first carry a
 * DUPLICATE mark; guardian portal downloads never count as prints. A bounced payment's
 * receipt still renders, marked CANCELLED.
 *
 * Two steps on purpose: {@link #prepare} runs in a transaction (locks the payment, writes
 * the register row), {@link #renderPdf} does the slow layout work outside it.
 */
@Service
public class ReceiptService {

	public static final String SUBJECT_TYPE = "PAYMENT";

	public record ReceiptStudent(String name, String admissionNumber, String classSection) {
	}

	public record ReceiptLine(String studentName, String admissionNumber, String invoiceNumber, String feeHead, LocalDate dueDate,
			BigDecimal amount, String amountText) {
	}

	/** Everything printed, frozen at first issue. Formatted text is frozen too, so a later currency change can't alter an old receipt. */
	public record ReceiptSnapshot(String receiptNumber, LocalDate paymentDate, String academicYear, BigDecimal amount, String amountText,
			String amountInWords, String paymentMethod, String paymentReference, String paymentBankName, String remarks,
			List<ReceiptStudent> students, List<ReceiptLine> lines, BigDecimal outstanding, String outstandingText,
			LocalDate outstandingAsOf, String collectedBy, DocumentBranding school, Instant issuedAt) {
	}

	public record PreparedReceipt(ReceiptSnapshot snapshot, boolean duplicate, int printNumber, boolean cancelled, PageFormat pageFormat,
			TemplateStyle templateStyle, int fontSize, String headerSubtext) {
	}

	private final PaymentRepository paymentRepository;
	private final PaymentAllocationRepository paymentAllocationRepository;
	private final InvoiceRepository invoiceRepository;
	private final IssuedDocumentRepository issuedDocumentRepository;
	private final DocumentTemplateRepository documentTemplateRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final DocumentBrandingService brandingService;
	private final DocumentHtmlRenderer htmlRenderer;
	private final PdfRenderer pdfRenderer;
	private final ObjectMapper objectMapper;

	public ReceiptService(PaymentRepository paymentRepository, PaymentAllocationRepository paymentAllocationRepository,
			InvoiceRepository invoiceRepository, IssuedDocumentRepository issuedDocumentRepository,
			DocumentTemplateRepository documentTemplateRepository, OrganisationMembershipRepository membershipRepository,
			DocumentBrandingService brandingService, DocumentHtmlRenderer htmlRenderer, PdfRenderer pdfRenderer, ObjectMapper objectMapper) {
		this.paymentRepository = paymentRepository;
		this.paymentAllocationRepository = paymentAllocationRepository;
		this.invoiceRepository = invoiceRepository;
		this.issuedDocumentRepository = issuedDocumentRepository;
		this.documentTemplateRepository = documentTemplateRepository;
		this.membershipRepository = membershipRepository;
		this.brandingService = brandingService;
		this.htmlRenderer = htmlRenderer;
		this.pdfRenderer = pdfRenderer;
		this.objectMapper = objectMapper;
	}

	/** @param officePrint true for a staff print (counted; second and later are DUPLICATE), false for a guardian's own download. */
	@Transactional
	public PreparedReceipt prepare(Long paymentId, boolean officePrint) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("No payment with id " + paymentId));

		IssuedDocument document = issuedDocumentRepository
				.findFirstByDocumentKindAndSubjectTypeAndSubjectIdOrderByIdDesc(IssuedDocumentKind.RECEIPT, SUBJECT_TYPE, paymentId)
				.orElseGet(() -> issue(payment));

		boolean cancelled = payment.getStatus() == PaymentStatus.BOUNCED;
		if (cancelled) {
			document.cancel("Payment bounced");
		}
		if (officePrint) {
			document.recordPrint(TenantContext.getActorId());
		}
		issuedDocumentRepository.save(document);

		DocumentTemplate template = documentTemplateRepository.findByDocumentType(DocumentType.RECEIPT)
				.orElseGet(() -> new DocumentTemplate(DocumentType.RECEIPT));
		return new PreparedReceipt(readSnapshot(document), officePrint && document.getPrintCount() > 1, document.getPrintCount(), cancelled,
				PageFormat.fromCode(template.getPageSize()), template.getTemplateStyle(), template.getFontSize(), template.getHeaderSubtext());
	}

	public byte[] renderPdf(PreparedReceipt receipt) {
		DocumentBranding school = receipt.snapshot().school();
		Map<String, Object> variables = new LinkedHashMap<>();
		variables.put("r", receipt);
		variables.put("s", receipt.snapshot());
		variables.put("page", receipt.pageFormat());
		variables.put("logo", brandingService.imageDataUri(school.logoRef()).orElse(null));
		variables.put("stamp", brandingService.imageDataUri(school.stampRef()).orElse(null));
		variables.put("signature", brandingService.imageDataUri(school.signatureRef()).orElse(null));
		variables.put("styleClass", "style-" + receipt.templateStyle().name().toLowerCase());
		return pdfRenderer.render(htmlRenderer.render("receipt", variables));
	}

	private IssuedDocument issue(Payment payment) {
		ReceiptSnapshot snapshot = buildSnapshot(payment);
		String json = objectMapper.writeValueAsString(snapshot);
		return issuedDocumentRepository.save(new IssuedDocument(IssuedDocumentKind.RECEIPT, payment.getReceiptNumber(), SUBJECT_TYPE,
				payment.getId(), json, sha256(json)));
	}

	private ReceiptSnapshot buildSnapshot(Payment payment) {
		DocumentBranding school = brandingService.current();
		String currency = school.currencyCode();
		List<PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(payment.getId());

		Map<Long, StudentEnrollment> enrollments = new LinkedHashMap<>();
		List<ReceiptLine> lines = new ArrayList<>();
		for (PaymentAllocation allocation : allocations) {
			Invoice invoice = allocation.getInvoice();
			StudentEnrollment enrollment = invoice.getStudentFeeAssignment().getStudentEnrollment();
			enrollments.putIfAbsent(enrollment.getId(), enrollment);
			FeeStructureInstallment installment = invoice.getFeeStructureInstallment();
			String feeHead = installment.getFeeStructure().getFeeCategory().getName() + " - Installment " + installment.getInstallmentNumber();
			lines.add(new ReceiptLine(fullName(enrollment.getStudent().getPerson()), enrollment.getStudent().getAdmissionNumber(),
					invoice.getInvoiceNumber(), feeHead, invoice.getDueDate(), allocation.getAllocatedAmount(),
					MoneyText.format(allocation.getAllocatedAmount(), currency)));
		}

		List<ReceiptStudent> students = new ArrayList<>();
		BigDecimal outstanding = BigDecimal.ZERO;
		for (StudentEnrollment enrollment : enrollments.values()) {
			students.add(new ReceiptStudent(fullName(enrollment.getStudent().getPerson()), enrollment.getStudent().getAdmissionNumber(),
					enrollment.getSection().getSchoolClass().label() + " - " + enrollment.getSection().getName()));
			for (Invoice invoice : invoiceRepository.findByStudentFeeAssignment_StudentEnrollmentId(enrollment.getId())) {
				outstanding = outstanding.add(invoice.getTotalAmount().subtract(invoice.getAmountPaid()).max(BigDecimal.ZERO));
			}
		}

		return new ReceiptSnapshot(payment.getReceiptNumber(), payment.getPaymentDate(), payment.getAcademicYear().getName(),
				payment.getAmount(), MoneyText.format(payment.getAmount(), currency), MoneyText.inWords(payment.getAmount(), currency),
				payment.getPaymentMethod().name().replace('_', ' '), payment.getPaymentReference(), payment.getPaymentBankName(),
				payment.getRemarks(), students, lines, outstanding, MoneyText.format(outstanding, currency), LocalDate.now(),
				collectedBy(payment.getCreatedBy()), school, Instant.now());
	}

	private String collectedBy(Long userId) {
		if (userId == null) {
			return null;
		}
		return membershipRepository.findByUserId(userId).stream()
				.filter(m -> m.getPerson() != null)
				.findFirst()
				.map(m -> fullName(m.getPerson()))
				.orElse(null);
	}

	private ReceiptSnapshot readSnapshot(IssuedDocument document) {
		return objectMapper.readValue(document.getDataSnapshot(), ReceiptSnapshot.class);
	}

	private static String fullName(Person person) {
		return person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
