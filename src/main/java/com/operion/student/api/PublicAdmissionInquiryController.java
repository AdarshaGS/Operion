package com.operion.student.api;

import java.util.List;
import java.util.Map;

import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.common.TenantContext;
import com.operion.common.TooManyRequestsException;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Applicant;
import com.operion.student.ApplicantInquiryService;
import com.operion.student.InquiryRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated admission inquiry form, whitelisted in JwtAuthenticationInterceptor.isPublic().
 * No class-level @RequirePermission. The org is resolved from the slug here and TenantContext set
 * BEFORE the service's transaction opens (tenant id resolves once per session). No file upload by design.
 */
@RestController
@RequestMapping("/api/v1/public/orgs/{slug}/admission-inquiries")
public class PublicAdmissionInquiryController {

	public record GradeOption(Long id, String name) {
	}

	public record FormOptionsResponse(String organisationName, List<GradeOption> grades) {
	}

	private final OrganisationRepository organisationRepository;
	private final GradeLevelRepository gradeLevelRepository;
	private final ApplicantInquiryService inquiryService;
	private final InquiryRateLimiter rateLimiter;

	public PublicAdmissionInquiryController(OrganisationRepository organisationRepository, GradeLevelRepository gradeLevelRepository,
			ApplicantInquiryService inquiryService, InquiryRateLimiter rateLimiter) {
		this.organisationRepository = organisationRepository;
		this.gradeLevelRepository = gradeLevelRepository;
		this.inquiryService = inquiryService;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/form-options")
	public FormOptionsResponse formOptions(@PathVariable String slug) {
		Organisation organisation = resolve(slug);
		TenantContext.set(organisation.getId(), null);
		List<GradeOption> grades = gradeLevelRepository.findByStatusOrderBySequenceOrderAsc(GradeLevelStatus.ACTIVE).stream()
				.map(grade -> new GradeOption(grade.getId(), grade.getName())).toList();
		return new FormOptionsResponse(organisation.getName(), grades);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Long> submit(@PathVariable String slug, @Valid @RequestBody SubmitAdmissionInquiryRequest request,
			HttpServletRequest httpRequest) {
		if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr() + "|" + slug)) {
			throw new TooManyRequestsException("Too many inquiries submitted. Please try again later.");
		}
		if (request.website() != null && !request.website().isBlank()) {
			// Honeypot tripped: look successful to the bot, store nothing.
			return java.util.Collections.singletonMap("id", null);
		}
		Organisation organisation = resolve(slug);
		TenantContext.set(organisation.getId(), null);
		Applicant applicant = inquiryService.submit(request.firstName(), request.lastName(), request.dateOfBirth(), request.gender(),
				request.desiredGradeId(), request.guardianName(), request.guardianPhone(), request.guardianEmail(), request.notes());
		return Map.of("id", applicant.getId());
	}

	private Organisation resolve(String slug) {
		return organisationRepository.findBySlug(slug)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with slug " + slug));
	}
}
