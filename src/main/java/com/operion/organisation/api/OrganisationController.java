package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal smoke-test surface for the Foundation module - lets the static UI
 * (src/main/resources/static/index.html) exercise provisioning, login and status
 * transitions end to end. Not the final API design (see ai-context/erp-system-plan.md
 * §5). Creation alone is public (bootstrapping); everything else requires the token
 * issued by /api/v1/auth/login, per JwtAuthenticationInterceptor.
 *
 * list()/get() (returning every organisation to any authenticated caller from any org -
 * a real cross-tenant visibility gap, since Organisation is the tenant root and can't
 * carry @TenantId itself) have been removed and replaced by
 * PlatformOrganisationController, reachable only via a platform-admin token.
 */
@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

	private final OrganisationService organisationService;

	public OrganisationController(OrganisationService organisationService) {
		this.organisationService = organisationService;
	}

	@PostMapping
	public OrganisationResponse create(@RequestBody CreateOrganisationRequest request) {
		Organisation organisation = organisationService.provision(request.toOrganisation(), request.toProfile(),
				request.toAdminAccount(), request.toAcademicYearDetails(), request.toPlanSelection());
		return OrganisationResponse.from(organisation);
	}

	@PatchMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationResponse changeStatus(@PathVariable Long id, @RequestBody ChangeOrganisationStatusRequest request) {
		OrganisationStatus target = OrganisationStatus.valueOf(request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target));
	}
}
