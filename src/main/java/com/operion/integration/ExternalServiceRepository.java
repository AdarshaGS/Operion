package com.operion.integration;

import com.operion.common.persistence.PlatformPlane;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface ExternalServiceRepository extends JpaRepository<ExternalService, Long> {

	Optional<ExternalService> findByServiceKey(String serviceKey);
}
