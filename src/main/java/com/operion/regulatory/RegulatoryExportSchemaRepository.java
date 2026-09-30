package com.operion.regulatory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulatoryExportSchemaRepository extends JpaRepository<RegulatoryExportSchema, Long> {

	Optional<RegulatoryExportSchema> findByCode(String code);

	List<RegulatoryExportSchema> findByEntityType(RegulatoryEntityType entityType);
}
