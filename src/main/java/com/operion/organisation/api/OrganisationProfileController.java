package com.operion.organisation.api;

import com.operion.authorization.RequirePermission;
import com.operion.common.InvalidRequestException;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import jakarta.validation.Valid;
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
	public OrganisationProfileResponse update(@Valid @RequestBody UpdateOrganisationProfileRequest request) {
		Organisation organisation = currentOrganisation();
		if (request.name() != null && request.name().isBlank()) {
			throw new InvalidRequestException("The organisation name cannot be blank");
		}
		organisation.setName(request.name() != null ? request.name().trim() : organisation.getName());
		organisation.setLegalName(keep(organisation.getLegalName(), request.legalName()));
		organisationRepository.save(organisation);

		OrganisationConfiguration configuration = currentConfiguration();
		configuration.setLogoUrl(keep(configuration.getLogoUrl(), request.logoUrl()));
		configuration.setPrimaryContactName(keep(configuration.getPrimaryContactName(), request.primaryContactName()));
		configuration.setPrimaryContactEmail(keep(configuration.getPrimaryContactEmail(), request.primaryContactEmail()));
		configuration.setPrimaryContactPhone(keep(configuration.getPrimaryContactPhone(), request.primaryContactPhone()));
		configuration.setAddressLine1(keep(configuration.getAddressLine1(), request.addressLine1()));
		configuration.setAddressLine2(keep(configuration.getAddressLine2(), request.addressLine2()));
		configuration.setCity(keep(configuration.getCity(), request.city()));
		configuration.setState(keep(configuration.getState(), request.state()));
		configuration.setCountry(keep(configuration.getCountry(), request.country()));
		configuration.setPincode(keep(configuration.getPincode(), request.pincode()));
		configuration.setTaxIdentifier(keep(configuration.getTaxIdentifier(), request.taxIdentifier()));
		configurationRepository.save(configuration);

		return OrganisationProfileResponse.from(organisation, configuration);
	}

	/** Null keeps the stored value; an empty string clears it. */
	private static String keep(String current, String sent) {
		if (sent == null) {
			return current;
		}
		return sent.isBlank() ? null : sent.trim();
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
