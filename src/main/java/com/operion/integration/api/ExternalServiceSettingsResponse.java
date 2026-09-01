package com.operion.integration.api;

import java.util.List;

public record ExternalServiceSettingsResponse(String serviceKey, String displayName, boolean enabled,
		List<ExternalServicePropertyStatusResponse> properties) {
}
