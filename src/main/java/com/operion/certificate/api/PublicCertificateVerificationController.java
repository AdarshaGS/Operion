package com.operion.certificate.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.operion.certificate.CertificateService;
import com.operion.certificate.CertificateService.VerificationResult;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationRepository;

/**
 * Target of the QR code printed on every certificate - no login. Shows only what is needed to tell a genuine
 * certificate from a forged or cancelled one (kind, number, issue date, school, student name, status); an
 * unknown school or token gets the same 404, so the endpoint does not reveal which schools exist.
 */
@RestController
@RequestMapping("/api/v1/public/orgs/{slug}/certificates")
public class PublicCertificateVerificationController {

	private final OrganisationRepository organisationRepository;
	private final CertificateService certificateService;

	public PublicCertificateVerificationController(OrganisationRepository organisationRepository, CertificateService certificateService) {
		this.organisationRepository = organisationRepository;
		this.certificateService = certificateService;
	}

	@GetMapping("/{token}")
	public VerificationResult verify(@PathVariable String slug, @PathVariable String token) {
		Long organisationId = organisationRepository.findBySlug(slug)
				.orElseThrow(() -> new IllegalArgumentException("No such certificate")).getId();
		TenantContext.set(organisationId, null);
		return certificateService.verify(token).orElseThrow(() -> new IllegalArgumentException("No such certificate"));
	}
}
