package com.operion.integration;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalServiceRepository extends JpaRepository<ExternalService, Long> {

	Optional<ExternalService> findByServiceKey(String serviceKey);
}
