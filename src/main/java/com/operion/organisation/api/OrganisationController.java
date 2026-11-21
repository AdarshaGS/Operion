package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.EnumParam;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.OrganisationStatus;
import com.operion.platform.settings.PlatformSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal surface for an organisation's own admin to change its status. Every call
 * requires the token issued by /api/v1/auth/login, per JwtAuthenticationInterceptor.
 * Organisation creation is NOT available here: only a platform admin can create one, via
 * PlatformOrganisationController (POST /api/v1/platform/organisations).
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
	private final PlatformSettingsService platformSettingsService;

	public OrganisationController(OrganisationService organisationService, PlatformSettingsService platformSettingsService) {
		this.organisationService = organisationService;
		this.platformSettingsService = platformSettingsService;
	}

	@PatchMapping("/{id}/status")
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeOrganisationStatusRequest request) {
		// An organisation's own admin changes only its own status; the platform console handles any other.
		if (!id.equals(TenantContext.getOrganisationId())) {
			throw new IllegalArgumentException("No organisation with id " + id);
		}
		OrganisationStatus target = EnumParam.parse(OrganisationStatus.class, request.status());
		return OrganisationResponse.from(organisationService.changeStatus(id, target), platformSettingsService.current().getTrialDays());
	}
}
