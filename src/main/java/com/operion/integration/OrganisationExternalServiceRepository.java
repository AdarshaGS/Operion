package com.operion.integration;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface OrganisationExternalServiceRepository extends JpaRepository<OrganisationExternalService, Long> {

	List<OrganisationExternalService> findByOrganisationId(Long organisationId);

	Optional<OrganisationExternalService> findByOrganisationIdAndExternalServiceId(Long organisationId, Long externalServiceId);
}
