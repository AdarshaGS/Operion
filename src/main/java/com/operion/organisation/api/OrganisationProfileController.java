package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organisation Profile (GitHub #141) - name/legal name (on {@link Organisation}) plus
 * logo, primary contact, address, and tax identifier (on {@link OrganisationConfiguration},
 * which already holds the org's other rarely-changing settings). Deliberately separate
 * from {@link OrganisationConfigurationController} (Business settings: timezone/currency/
 * working days) even though both save onto the same configuration row - two forms, one
 * underlying record, no duplicated storage. Always resolves the caller's own org from
 * TenantContext, same reasoning as OrganisationConfigurationController.
 */
@RestController
@RequestMapping("/api/v1/organisations/profile")
public class OrganisationProfileController {

	private final OrganisationRepository organisationRepository;
	private final OrganisationConfigurationRepository configurationRepository;

	public OrganisationProfileController(OrganisationRepository organisationRepository,
			OrganisationConfigurationRepository configurationRepository) {
		this.organisationRepository = organisationRepository;
		this.configurationRepository = configurationRepository;
	}

	@GetMapping
	public OrganisationProfileResponse get() {
		return OrganisationProfileResponse.from(currentOrganisation(), currentConfiguration());
	}

	@PatchMapping
	@RequirePermission("ORGANISATION_MANAGE")
	public OrganisationProfileResponse update(@RequestBody UpdateOrganisationProfileRequest request) {
		Organisation organisation = currentOrganisation();
		organisation.setName(request.name());
		organisation.setLegalName(request.legalName());
		organisationRepository.save(organisation);

		OrganisationConfiguration configuration = currentConfiguration();
		configuration.setLogoUrl(request.logoUrl());
		configuration.setPrimaryContactName(request.primaryContactName());
		configuration.setPrimaryContactEmail(request.primaryContactEmail());
		configuration.setPrimaryContactPhone(request.primaryContactPhone());
		configuration.setAddressLine1(request.addressLine1());
		configuration.setAddressLine2(request.addressLine2());
		configuration.setCity(request.city());
		configuration.setState(request.state());
		configuration.setPincode(request.pincode());
		configuration.setTaxIdentifier(request.taxIdentifier());
		configurationRepository.save(configuration);

		return OrganisationProfileResponse.from(organisation, configuration);
	}

	private Organisation currentOrganisation() {
		Long organisationId = TenantContext.getOrganisationId();
		return organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
	}

	private OrganisationConfiguration currentConfiguration() {
		Long organisationId = TenantContext.getOrganisationId();
		return configurationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No configuration for organisation " + organisationId));
	}
}
