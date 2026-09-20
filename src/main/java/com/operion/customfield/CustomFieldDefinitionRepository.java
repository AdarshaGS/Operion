package com.operion.customfield;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, Long> {

	List<CustomFieldDefinition> findByEntityType(String entityType);

	List<CustomFieldDefinition> findByEntityTypeAndStatus(String entityType, CustomFieldDefinitionStatus status);
}
