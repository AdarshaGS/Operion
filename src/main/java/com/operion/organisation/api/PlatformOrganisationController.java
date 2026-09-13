package com.operion.organisation.api;

import java.util.List;

import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import com.operion.platform.settings.PlatformSettingsService;
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
	private final PlatformSettingsService platformSettingsService;

	public PlatformOrganisationController(OrganisationRepository organisationRepository, OrganisationService organisationService,
			PlatformSettingsService platformSettingsService) {
		this.organisationRepository = organisationRepository;
		this.organisationService = organisationService;
		this.platformSettingsService = platformSettingsService;
	}

	@GetMapping
	public List<OrganisationResponse> list() {
		int trialDays = platformSettingsService.current().getTrialDays();
		return organisationRepository.findAll().stream().map(org -> OrganisationResponse.from(org, trialDays)).toList();
	}

	@GetMapping("/{id}")
	public OrganisationResponse get(@PathVariable Long id) {
		return OrganisationResponse.from(organisationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + id)),
				platformSettingsService.current().getTrialDays());
	}

	@PostMapping
	public OrganisationResponse create(@RequestBody CreateOrganisationRequest request) {
		Organisation organisation = organisationService.provision(request.toOrganisation(), request.toProfile(),
				request.toAdminAccount(), request.toAcademicYearDetails(), request.toPlanSelection());
		return OrganisationResponse.from(organisation, platformSettingsService.current().getTrialDays());
	}

	@PatchMapping("/{id}/status")
	public OrganisationResponse changeStatus(@PathVariable Long id, @RequestBody ChangeOrganisationStatusRequest request) {
		OrganisationStatus target = OrganisationStatus.valueOf(request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target), platformSettingsService.current().getTrialDays());
	}
}
