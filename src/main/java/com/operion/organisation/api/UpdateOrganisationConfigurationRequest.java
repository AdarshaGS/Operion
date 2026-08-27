package com.operion.organisation.api;

public record UpdateOrganisationConfigurationRequest(String timezone, String defaultCurrency, String dateFormat,
		int workingDaysMask, String logoUrl, String primaryColor) {
}
