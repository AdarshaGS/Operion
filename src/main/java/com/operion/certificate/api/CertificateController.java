package com.operion.certificate.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.operion.authorization.RequirePermission;
import com.operion.certificate.CertificateRequest;
import com.operion.certificate.CertificateRequestChannel;
import com.operion.certificate.CertificateRequestStatus;
import com.operion.certificate.CertificateService;
import com.operion.certificate.CertificateService.Clearance;
import com.operion.certificate.CertificateService.PreparedCertificate;
import com.operion.document.pdf.PdfResponses;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Staff side of certificates: raise, approve or reject, check dues, issue, print and cancel. */
@RestController
@RequestMapping("/api/v1/certificates")
public class CertificateController {

	public record RaiseCertificateRequest(@NotNull Long studentId, @NotNull Long certificateTypeId, @Size(max = 255) String purpose,
			Map<String, String> manualValues) {
	}

	public record UpdateCertificateDetailsRequest(@Size(max = 255) String purpose, Map<String, String> manualValues) {
	}

	public record ReasonRequest(@Size(max = 500) String reason) {
	}

	private final CertificateService certificateService;

	public CertificateController(CertificateService certificateService) {
		this.certificateService = certificateService;
	}

	@GetMapping
	@RequirePermission(anyOf = { "CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_APPROVE", "CERTIFICATE_ISSUE" })
	public List<CertificateRequestResponse> list(@RequestParam(required = false) CertificateRequestStatus status,
			@RequestParam(required = false) Long studentId) {
		return certificateService.list(status, studentId).stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	@RequirePermission(anyOf = { "CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_APPROVE", "CERTIFICATE_ISSUE" })
	public CertificateRequestResponse get(@PathVariable Long id) {
		return toResponse(certificateService.get(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@RequirePermission("CERTIFICATE_REQUEST")
	public CertificateRequestResponse raise(@Valid @RequestBody RaiseCertificateRequest request) {
		return toResponse(certificateService.request(request.studentId(), request.certificateTypeId(), request.purpose(), request.manualValues(),
				CertificateRequestChannel.STAFF));
	}

	@PutMapping("/{id}")
	@RequirePermission(anyOf = { "CERTIFICATE_REQUEST", "CERTIFICATE_ISSUE" })
	public CertificateRequestResponse updateDetails(@PathVariable Long id, @Valid @RequestBody UpdateCertificateDetailsRequest request) {
		return toResponse(certificateService.updateDetails(id, request.purpose(), request.manualValues()));
	}

	@PostMapping("/{id}/approve")
	@RequirePermission("CERTIFICATE_APPROVE")
	public CertificateRequestResponse approve(@PathVariable Long id) {
		return toResponse(certificateService.approve(id));
	}

	@PostMapping("/{id}/reject")
	@RequirePermission("CERTIFICATE_APPROVE")
	public CertificateRequestResponse reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return toResponse(certificateService.reject(id, request.reason()));
	}

	@GetMapping("/{id}/clearance")
	@RequirePermission(anyOf = { "CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_APPROVE", "CERTIFICATE_ISSUE" })
	public Clearance clearance(@PathVariable Long id) {
		return certificateService.clearance(id);
	}

	/** Body may carry {@code reason} = the clearance override reason, needed only when dues are outstanding. */
	@PostMapping("/{id}/issue")
	@RequirePermission("CERTIFICATE_ISSUE")
	public CertificateRequestResponse issue(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest request) {
		return toResponse(certificateService.issue(id, request == null ? null : request.reason()));
	}

	@PostMapping("/{id}/cancel")
	@RequirePermission("CERTIFICATE_CANCEL")
	public CertificateRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return toResponse(certificateService.cancel(id, request.reason()));
	}

	@GetMapping("/{id}/certificate.pdf")
	@RequirePermission("CERTIFICATE_ISSUE")
	public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
		PreparedCertificate certificate = certificateService.prepare(id, true);
		return PdfResponses.attachment(certificateService.renderPdf(certificate), certificate.snapshot().certificateNumber());
	}

	private CertificateRequestResponse toResponse(CertificateRequest request) {
		return CertificateRequestResponse.from(request, certificateService.readManual(request));
	}
}
