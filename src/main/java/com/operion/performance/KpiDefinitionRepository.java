package com.operion.performance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KpiDefinitionRepository extends JpaRepository<KpiDefinition, Long> {

	Optional<KpiDefinition> findByMetric(KpiMetric metric);
}
