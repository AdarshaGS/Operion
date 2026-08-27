package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
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

	public OrganisationConfigurationController(OrganisationConfigurationRepository configurationRepository) {
		this.configurationRepository = configurationRepository;
	}

	@GetMapping
	public OrganisationConfigurationResponse get() {
		return OrganisationConfigurationResponse.from(currentConfiguration());
	}

	@PatchMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationConfigurationResponse update(@RequestBody UpdateOrganisationConfigurationRequest request) {
		OrganisationConfiguration configuration = currentConfiguration();
		configuration.setTimezone(request.timezone());
		configuration.setDefaultCurrency(request.defaultCurrency());
		configuration.setDateFormat(request.dateFormat());
		configuration.setWorkingDaysMask(request.workingDaysMask());
		configuration.setLogoUrl(request.logoUrl());
		configuration.setPrimaryColor(request.primaryColor());
		return OrganisationConfigurationResponse.from(configurationRepository.save(configuration));
	}

	private OrganisationConfiguration currentConfiguration() {
		Long organisationId = TenantContext.getOrganisationId();
		return configurationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No configuration for organisation " + organisationId));
	}
}
