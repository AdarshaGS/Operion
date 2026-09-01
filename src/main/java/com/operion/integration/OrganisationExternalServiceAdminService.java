package com.operion.integration;

import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-admin write side: grants or revokes an organisation's entitlement to
 * configure a given integration. Never touches that organisation's own credential values
 * - see OrganisationExternalServiceProperty's doc comment for why this plane can't. */
@Service
public class OrganisationExternalServiceAdminService {

	private final OrganisationRepository organisationRepository;
	private final ExternalServiceRepository externalServiceRepository;
	private final OrganisationExternalServiceRepository organisationExternalServiceRepository;

	public OrganisationExternalServiceAdminService(OrganisationRepository organisationRepository,
			ExternalServiceRepository externalServiceRepository, OrganisationExternalServiceRepository organisationExternalServiceRepository) {
		this.organisationRepository = organisationRepository;
		this.externalServiceRepository = externalServiceRepository;
		this.organisationExternalServiceRepository = organisationExternalServiceRepository;
	}

	@Transactional
	public OrganisationExternalService setEnabled(Long organisationId, String serviceKey, boolean enabled) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
		ExternalService service = externalServiceRepository.findByServiceKey(serviceKey)
				.orElseThrow(() -> new IllegalArgumentException("No external service with key " + serviceKey));

		OrganisationExternalService entitlement = organisationExternalServiceRepository
				.findByOrganisationIdAndExternalServiceId(organisationId, service.getId())
				.orElseGet(() -> new OrganisationExternalService(organisation, service, enabled));
		entitlement.setEnabled(enabled);
		return organisationExternalServiceRepository.save(entitlement);
	}
}
