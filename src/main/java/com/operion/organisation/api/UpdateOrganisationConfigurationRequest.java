package com.operion.organisation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** workingDaysMask is a 7-bit week mask, so 1 to 127. primaryColor is a #RRGGBB hex colour (or empty/null for none). */
public record UpdateOrganisationConfigurationRequest(String timezone, String defaultCurrency, String dateFormat,
		@Min(1) @Max(127) int workingDaysMask, String logoUrl,
		@Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "primaryColor must be a #RRGGBB hex colour") String primaryColor) {
}
