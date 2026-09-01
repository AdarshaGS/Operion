package com.operion.integration;

import java.util.Optional;

import com.operion.common.TenantContext;
import org.springframework.stereotype.Component;

/**
 * The read seam a caller reaches for a 3rd-party credential (see BrevoEmailSender,
 * BrevoSmsSender) instead of talking to the repositories directly. Resolves for
 * {@code TenantContext.getOrganisationId()} - every real call site already runs with it
 * set (NotificationDispatchWorker sets it per organisation before dispatching; every
 * other caller runs inside a request that already carries it, same assumption
 * OrganisationBrandingController makes) - so callers don't need to thread an organisation
 * id through themselves. Resolved fresh per call, not cached, so a platform-admin
 * enable/disable or an organisation admin's saved edit takes effect on the next send, no
 * restart needed.
 */
@Component
public class ExternalServiceCredentialResolver {

	private final ExternalServiceRepository externalServiceRepository;
	private final OrganisationExternalServiceRepository organisationExternalServiceRepository;
	private final OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository;
	private final ExternalServiceSecretCipher cipher;

	ExternalServiceCredentialResolver(ExternalServiceRepository externalServiceRepository,
			OrganisationExternalServiceRepository organisationExternalServiceRepository,
			OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository, ExternalServiceSecretCipher cipher) {
		this.externalServiceRepository = externalServiceRepository;
		this.organisationExternalServiceRepository = organisationExternalServiceRepository;
		this.organisationExternalServicePropertyRepository = organisationExternalServicePropertyRepository;
		this.cipher = cipher;
	}

	/** Empty if there's no current organisation, the service doesn't exist, the
	 * organisation isn't entitled to it (no row, or {@code enabled=false}), or the
	 * organisation hasn't saved a non-blank value for that property yet - callers treat
	 * all of these as "not configured". */
	public Optional<String> resolve(String serviceKey, String propertyKey) {
		Long organisationId = TenantContext.getOrganisationId();
		if (organisationId == null) {
			return Optional.empty();
		}

		return externalServiceRepository.findByServiceKey(serviceKey)
				.flatMap(service -> organisationExternalServiceRepository.findByOrganisationIdAndExternalServiceId(organisationId, service.getId())
						.filter(OrganisationExternalService::isEnabled)
						.flatMap(entitlement -> organisationExternalServicePropertyRepository.findByExternalServiceIdAndPropertyKey(service.getId(),
								propertyKey)))
				.filter(property -> property.getPropertyValue() != null && !property.getPropertyValue().isBlank())
				.map(property -> property.isSecret() ? cipher.decrypt(property.getPropertyValue()) : property.getPropertyValue());
	}
}
