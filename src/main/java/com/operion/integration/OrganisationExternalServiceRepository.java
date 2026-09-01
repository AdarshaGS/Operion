package com.operion.integration;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationExternalServiceRepository extends JpaRepository<OrganisationExternalService, Long> {

	List<OrganisationExternalService> findByOrganisationId(Long organisationId);

	Optional<OrganisationExternalService> findByOrganisationIdAndExternalServiceId(Long organisationId, Long externalServiceId);
}
