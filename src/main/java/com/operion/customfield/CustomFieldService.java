package com.operion.customfield;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * (De)serializes the JSON blob stored in an opted-in entity's own {@code custom_fields}
 * column (see {@link CustomFieldDefinition}'s class comment) and validates it against
 * that entity type's ACTIVE definitions before it's saved - the one piece of shared logic
 * every opted-in entity's service (starting with {@code com.operion.student.StudentService})
 * would otherwise have to duplicate.
 */
@Service
public class CustomFieldService {

	private final CustomFieldDefinitionRepository definitionRepository;
	private final ObjectMapper objectMapper;

	public CustomFieldService(CustomFieldDefinitionRepository definitionRepository, ObjectMapper objectMapper) {
		this.definitionRepository = definitionRepository;
		this.objectMapper = objectMapper;
	}

	/** Keeps only keys that match an ACTIVE definition for this entity type (an archived
	 * or unrecognized key is silently dropped, not stored), then requires every ACTIVE
	 * required definition to have a non-blank value. Returns null (not "{}"'') when there
	 * are no values, so a record with nothing set stores a plain SQL NULL. */
	public String validateAndSerialize(String entityType, Map<String, Object> values) {
		var definitions = definitionRepository.findByEntityTypeAndStatus(entityType, CustomFieldDefinitionStatus.ACTIVE);
		Map<String, Object> input = values != null ? values : Map.of();
		Map<String, Object> filtered = new LinkedHashMap<>();
		for (CustomFieldDefinition definition : definitions) {
			Object value = input.get(definition.getFieldKey());
			if (definition.isRequired() && (value == null || value.toString().isBlank())) {
				throw new IllegalArgumentException("Custom field '" + definition.getLabel() + "' is required");
			}
			if (value != null && !value.toString().isBlank()) {
				filtered.put(definition.getFieldKey(), value);
			}
		}
		if (filtered.isEmpty()) {
			return null;
		}
		return objectMapper.writeValueAsString(filtered);
	}

	public Map<String, Object> deserialize(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
	}
}
