package com.operion.regulatory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulatoryExportFieldRepository extends JpaRepository<RegulatoryExportField, Long> {

	List<RegulatoryExportField> findBySchemaIdOrderByOrdinal(Long schemaId);
}
