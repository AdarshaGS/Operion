package com.operion.organisation.api;

import com.operion.organisation.OrganisationConfiguration;

public record OrganisationConfigurationResponse(String timezone, String defaultCurrency, String dateFormat,
		int workingDaysMask, String logoUrl, String primaryColor) {

	static OrganisationConfigurationResponse from(OrganisationConfiguration configuration) {
		return new OrganisationConfigurationResponse(configuration.getTimezone(), configuration.getDefaultCurrency(),
				configuration.getDateFormat(), configuration.getWorkingDaysMask(), configuration.getLogoUrl(),
				configuration.getPrimaryColor());
	}
}
