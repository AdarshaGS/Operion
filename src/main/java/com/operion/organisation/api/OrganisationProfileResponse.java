package com.operion.organisation.api;

import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;

public record OrganisationProfileResponse(String name, String legalName, String logoUrl, String primaryContactName,
		String primaryContactEmail, String primaryContactPhone, String addressLine1, String addressLine2, String city,
		String state, String country, String pincode, String taxIdentifier) {

	static OrganisationProfileResponse from(Organisation organisation, OrganisationConfiguration configuration) {
		return new OrganisationProfileResponse(organisation.getName(), organisation.getLegalName(), configuration.getLogoUrl(),
				configuration.getPrimaryContactName(), configuration.getPrimaryContactEmail(), configuration.getPrimaryContactPhone(),
				configuration.getAddressLine1(), configuration.getAddressLine2(), configuration.getCity(), configuration.getState(),
				configuration.getCountry(), configuration.getPincode(), configuration.getTaxIdentifier());
	}
}
