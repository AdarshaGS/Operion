package com.operion.integration.api;

import com.operion.integration.ExternalService;

public record OrganisationExternalServiceResponse(String serviceKey, String displayName, boolean enabled) {

	static OrganisationExternalServiceResponse of(ExternalService service, boolean enabled) {
		return new OrganisationExternalServiceResponse(service.getServiceKey(), service.getDisplayName(), enabled);
	}
}
