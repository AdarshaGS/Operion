package com.operion.integration;

import java.util.List;
import java.util.Map;

import com.operion.common.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Institution-admin write side: saves the current organisation's own values for one
 * service's properties. Refuses if the organisation isn't entitled to the service (see
 * OrganisationExternalService) - a platform admin must enable it first. */
@Service
public class OrganisationExternalServicePropertyService {

	private final ExternalServiceRepository externalServiceRepository;
	private final OrganisationExternalServiceRepository organisationExternalServiceRepository;
	private final OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository;
	private final ExternalServicePropertyCatalog propertyCatalog;
	private final ExternalServiceSecretCipher cipher;

	public OrganisationExternalServicePropertyService(ExternalServiceRepository externalServiceRepository,
			OrganisationExternalServiceRepository organisationExternalServiceRepository,
			OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository,
			ExternalServicePropertyCatalog propertyCatalog, ExternalServiceSecretCipher cipher) {
		this.externalServiceRepository = externalServiceRepository;
		this.organisationExternalServiceRepository = organisationExternalServiceRepository;
		this.organisationExternalServicePropertyRepository = organisationExternalServicePropertyRepository;
		this.propertyCatalog = propertyCatalog;
		this.cipher = cipher;
	}

	/** Only keys present in {@code updates} and defined in the service's property catalog
	 * are touched; a blank value clears that property back to unconfigured. */
	@Transactional
	public void updateProperties(String serviceKey, Map<String, String> updates) {
		Long organisationId = TenantContext.getOrganisationId();
		ExternalService service = externalServiceRepository.findByServiceKey(serviceKey)
				.orElseThrow(() -> new IllegalArgumentException("No external service with key " + serviceKey));

		boolean entitled = organisationExternalServiceRepository.findByOrganisationIdAndExternalServiceId(organisationId, service.getId())
				.map(OrganisationExternalService::isEnabled)
				.orElse(false);
		if (!entitled) {
			throw new IllegalStateException(serviceKey + " is not enabled for this organisation");
		}

		List<ExternalServicePropertyDefinition> definitions = propertyCatalog.propertiesFor(serviceKey);
		for (ExternalServicePropertyDefinition definition : definitions) {
			if (!updates.containsKey(definition.key())) {
				continue;
			}
			String value = updates.get(definition.key());
			value = value == null ? "" : value;

			OrganisationExternalServiceProperty property = organisationExternalServicePropertyRepository
					.findByExternalServiceIdAndPropertyKey(service.getId(), definition.key())
					.orElseGet(() -> new OrganisationExternalServiceProperty(service, definition.key(), "", definition.secret()));
			property.setPropertyValue(definition.secret() && !value.isBlank() ? cipher.encrypt(value) : value);
			organisationExternalServicePropertyRepository.save(property);
		}
	}
}
