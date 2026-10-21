package com.operion.portal.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.certificate.CertificateRequest;
import com.operion.certificate.CertificateRequestChannel;
import com.operion.certificate.CertificateRequestStatus;
import com.operion.certificate.CertificateService;
import com.operion.certificate.CertificateService.PreparedCertificate;
import com.operion.certificate.CertificateTypeService;
import com.operion.common.TenantContext;
import com.operion.document.pdf.PdfResponses;
import com.operion.modules.AppModule;
import com.operion.modules.RequireModule;
import com.operion.portal.GuardianPortalService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Guardian side of certificates: see a linked child's certificates, request the types the school lets guardians
 * request (bonafide by default), and download issued ones. Same data-scoped access as GuardianPortalController -
 * every call first proves the caller is linked to the child. Downloads are not counted as office prints.
 */
@RestController
@RequestMapping("/api/v1/me")
public class GuardianCertificateController {

	public record RequestableType(Long id, String name) {
	}

	public record GuardianCertificate(Long id, String certificateName, String status, String purpose, Instant requestedAt,
			String certificateNumber, Instant issuedAt, String rejectionReason) {
	}

	public record GuardianCertificateRequest(@NotNull Long certificateTypeId, @Size(max = 255) String purpose) {
	}

	private final GuardianPortalService guardianPortalService;
	private final CertificateService certificateService;
	private final CertificateTypeService certificateTypeService;

	public GuardianCertificateController(GuardianPortalService guardianPortalService, CertificateService certificateService,
			CertificateTypeService certificateTypeService) {
		this.guardianPortalService = guardianPortalService;
		this.certificateService = certificateService;
		this.certificateTypeService = certificateTypeService;
	}

	@GetMapping("/certificate-types")
	@RequireModule(AppModule.STUDENTS)
	public List<RequestableType> requestableTypes() {
		return certificateTypeService.list().stream().filter(type -> type.isActive() && type.isGuardianRequestable())
				.map(type -> new RequestableType(type.getId(), type.getName())).toList();
	}

	@GetMapping("/children/{studentId}/certificates")
	@RequireModule(AppModule.STUDENTS)
	public List<GuardianCertificate> list(@PathVariable Long studentId) {
		guardianPortalService.requireOwnChild(TenantContext.getActorId(), studentId);
		return certificateService.list(null, studentId).stream()
				.filter(request -> request.getRequestedVia() == CertificateRequestChannel.PORTAL || request.getIssuedDocument() != null)
				.map(GuardianCertificateController::toSummary).toList();
	}

	@PostMapping("/children/{studentId}/certificates")
	@ResponseStatus(HttpStatus.CREATED)
	@RequireModule(AppModule.STUDENTS)
	public GuardianCertificate request(@PathVariable Long studentId, @Valid @RequestBody GuardianCertificateRequest body) {
		guardianPortalService.requireOwnChild(TenantContext.getActorId(), studentId);
		return toSummary(certificateService.request(studentId, body.certificateTypeId(), body.purpose(), null, CertificateRequestChannel.PORTAL));
	}

	@GetMapping("/children/{studentId}/certificates/{requestId}/certificate.pdf")
	@RequireModule(AppModule.STUDENTS)
	public ResponseEntity<byte[]> download(@PathVariable Long studentId, @PathVariable Long requestId) {
		guardianPortalService.requireOwnChild(TenantContext.getActorId(), studentId);
		CertificateRequest request = certificateService.get(requestId);
		if (!request.getStudent().getId().equals(studentId) || request.getStatus() != CertificateRequestStatus.ISSUED) {
			throw new AuthorizationDeniedException("This certificate is not available for download");
		}
		PreparedCertificate certificate = certificateService.prepare(requestId, false);
		return PdfResponses.attachment(certificateService.renderPdf(certificate), certificate.snapshot().certificateNumber());
	}

	private static GuardianCertificate toSummary(CertificateRequest request) {
		return new GuardianCertificate(request.getId(), request.getCertificateType().getName(), request.getStatus().name(), request.getPurpose(),
				request.getCreatedAt(), request.getCertificateNumber(), request.getIssuedAt(), request.getRejectionReason());
	}
}
