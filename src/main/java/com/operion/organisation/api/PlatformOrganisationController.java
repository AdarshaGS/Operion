package com.operion.organisation.api;

import java.util.List;

import com.operion.organisation.NewAdminAccount;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cross-org visibility OrganisationController.list()/get() used to expose to any
 * authenticated user - moved here so it's reachable only via a platform-admin token
 * (mounted under /api/v1/platform/**, gated by PlatformAuthenticationInterceptor, never
 * by JwtAuthenticationInterceptor/PermissionInterceptor). create()/changeStatus() give
 * a platform admin the same provisioning/status-transition capability the public
 * OrganisationController exposes, without needing the school's own admin to self-serve it -
 * same OrganisationService methods, no new business logic.
 */
@RestController
@RequestMapping("/api/v1/platform/organisations")
public class PlatformOrganisationController {

	private final OrganisationRepository organisationRepository;
	private final OrganisationService organisationService;

	public PlatformOrganisationController(OrganisationRepository organisationRepository, OrganisationService organisationService) {
		this.organisationRepository = organisationRepository;
		this.organisationService = organisationService;
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

	@PostMapping
	public OrganisationResponse create(@RequestBody CreateOrganisationRequest request) {
		NewAdminAccount admin = new NewAdminAccount(
				request.adminEmail(), request.adminPassword(), request.adminFirstName(), request.adminLastName());
		Organisation organisation = organisationService.provision(request.name(), request.legalName(), request.slug(), admin);
		return OrganisationResponse.from(organisation);
	}

	@PatchMapping("/{id}/status")
	public OrganisationResponse changeStatus(@PathVariable Long id, @RequestBody ChangeOrganisationStatusRequest request) {
		OrganisationStatus target = OrganisationStatus.valueOf(request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target));
	}
}
