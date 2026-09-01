package com.operion.integration;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationExternalServicePropertyRepository extends JpaRepository<OrganisationExternalServiceProperty, Long> {

	List<OrganisationExternalServiceProperty> findByExternalServiceId(Long externalServiceId);

	Optional<OrganisationExternalServiceProperty> findByExternalServiceIdAndPropertyKey(Long externalServiceId, String propertyKey);
}
