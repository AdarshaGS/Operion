package com.operion.organisation.api;

import java.util.List;

import com.operion.organisation.OrganisationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cross-org visibility OrganisationController.list()/get() used to expose to any
 * authenticated user - moved here so it's reachable only via a platform-admin token
 * (mounted under /api/v1/platform/**, gated by PlatformAuthenticationInterceptor, never
 * by JwtAuthenticationInterceptor/PermissionInterceptor).
 */
@RestController
@RequestMapping("/api/v1/platform/organisations")
public class PlatformOrganisationController {

	private final OrganisationRepository organisationRepository;

	public PlatformOrganisationController(OrganisationRepository organisationRepository) {
		this.organisationRepository = organisationRepository;
	}

	@GetMapping
	public List<OrganisationResponse> list() {
		return organisationRepository.findAll().stream().map(OrganisationResponse::from).toList();
	}

	@GetMapping("/{id}")
	public OrganisationResponse get(@PathVariable Long id) {
		return OrganisationResponse.from(organisationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + id)));
	}
}
