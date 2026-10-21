package com.operion.performance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KpiSettingsRepository extends JpaRepository<KpiSettings, Long> {

	Optional<KpiSettings> findByOrganisationId(Long organisationId);
}
