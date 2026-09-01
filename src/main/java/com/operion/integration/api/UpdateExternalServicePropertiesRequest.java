package com.operion.integration.api;

import java.util.Map;

/** Only keys present in {@code properties} are updated - see
 * OrganisationExternalServicePropertyService. A blank value clears that property back to
 * unconfigured. */
public record UpdateExternalServicePropertiesRequest(Map<String, String> properties) {
}
