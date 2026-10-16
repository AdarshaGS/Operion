package com.operion.organisation.api;

import com.operion.audit.AuditLogService;
import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Always resolves the caller's own org from TenantContext rather than taking an id
 * path variable - Organisation itself carries no @TenantId (see PlatformOrganisationController's
 * class doc on that gap), so an id-addressed endpoint here would let any authenticated
 * member read/write another org's settings by changing the id in the URL. No dedicated
 * service - same "mutate + save" shape as CampusController. */
@RestController
@RequestMapping("/api/v1/organisations/settings")
public class OrganisationConfigurationController {

	private final OrganisationConfigurationRepository configurationRepository;

	private final AuditLogService auditLogService;

	public OrganisationConfigurationController(OrganisationConfigurationRepository configurationRepository,
			AuditLogService auditLogService) {
		this.configurationRepository = configurationRepository;
		this.auditLogService = auditLogService;
	}

	@GetMapping
	public OrganisationConfigurationResponse get() {
		return OrganisationConfigurationResponse.from(currentConfiguration());
	}

	@PatchMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationConfigurationResponse update(@Valid @RequestBody UpdateOrganisationConfigurationRequest request) {
		if (request.timezone() != null && !java.time.ZoneId.getAvailableZoneIds().contains(request.timezone())) {
			throw new InvalidRequestException("Unknown timezone " + request.timezone());
		}
		OrganisationConfiguration configuration = currentConfiguration();
		OrganisationConfigurationResponse before = OrganisationConfigurationResponse.from(configuration);
		configuration.setTimezone(request.timezone());
		configuration.setDefaultCurrency(request.defaultCurrency());
		configuration.setDateFormat(request.dateFormat());
		configuration.setWorkingDaysMask(request.workingDaysMask());
		configuration.setLogoUrl(request.logoUrl());
		configuration.setPrimaryColor(request.primaryColor());
		OrganisationConfigurationResponse after = OrganisationConfigurationResponse.from(configurationRepository.save(configuration));
		auditLogService.record("OrganisationConfiguration", configuration.getOrganisationId(), "SETTINGS_UPDATED", before, after);
		return after;
	}

	private OrganisationConfiguration currentConfiguration() {
		Long organisationId = TenantContext.getOrganisationId();
		return configurationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No configuration for organisation " + organisationId));
	}
}
