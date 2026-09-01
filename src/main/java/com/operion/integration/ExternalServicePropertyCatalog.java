package com.operion.integration;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Which property keys each service has, and which are secret - defined here in code
 * rather than in the database (see {@link ExternalService}'s doc comment for why). The
 * single source of truth for what {@link ExternalServiceSettingsController} and
 * {@link OrganisationExternalServicePropertyService} accept/render for a given service.
 */
@Component
public class ExternalServicePropertyCatalog {

	private static final Map<String, List<ExternalServicePropertyDefinition>> DEFINITIONS = Map.of("brevo",
			List.of(new ExternalServicePropertyDefinition("email.api-key", true),
					new ExternalServicePropertyDefinition("email.sender-email", false),
					new ExternalServicePropertyDefinition("email.sender-name", false),
					new ExternalServicePropertyDefinition("sms.api-key", true),
					new ExternalServicePropertyDefinition("sms.sender", false)));

	public List<ExternalServicePropertyDefinition> propertiesFor(String serviceKey) {
		return DEFINITIONS.getOrDefault(serviceKey, List.of());
	}
}
