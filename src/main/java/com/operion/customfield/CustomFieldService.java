package com.operion.customfield;

import java.util.LinkedHashMap;
import java.util.Map;

import com.operion.common.InvalidRequestException;
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
		return validateAndSerialize(entityType, values, null);
	}

	/** As above, but a value already stored under a key whose definition is now ARCHIVED is carried over:
	 * archiving a field hides it, it does not erase what records already hold. */
	public String validateAndSerialize(String entityType, Map<String, Object> values, String existingJson) {
		var definitions = definitionRepository.findByEntityTypeAndStatus(entityType, CustomFieldDefinitionStatus.ACTIVE);
		Map<String, Object> input = values != null ? values : Map.of();
		Map<String, Object> filtered = new LinkedHashMap<>();
		for (CustomFieldDefinition definition : definitions) {
			Object value = input.get(definition.getFieldKey());
			if (definition.isRequired() && (value == null || value.toString().isBlank())) {
				throw new InvalidRequestException("Custom field '" + definition.getLabel() + "' is required");
			}
			if (value != null && !value.toString().isBlank()) {
				requireMatchesType(definition, value);
				filtered.put(definition.getFieldKey(), value);
			}
		}
		if (existingJson != null) {
			Map<String, Object> existing = deserialize(existingJson);
			for (CustomFieldDefinition archived : definitionRepository.findByEntityTypeAndStatus(entityType, CustomFieldDefinitionStatus.ARCHIVED)) {
				Object kept = existing.get(archived.getFieldKey());
				if (kept != null && !filtered.containsKey(archived.getFieldKey())) {
					filtered.put(archived.getFieldKey(), kept);
				}
			}
		}
		if (filtered.isEmpty()) {
			return null;
		}
		return objectMapper.writeValueAsString(filtered);
	}

	private static void requireMatchesType(CustomFieldDefinition definition, Object value) {
		String text = value.toString().trim();
		boolean valid = switch (definition.getDataType()) {
			case NUMBER -> value instanceof Number || isNumber(text);
			case BOOLEAN -> value instanceof Boolean || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false");
			case DATE -> isDate(text);
			case SELECT -> definition.getOptions() == null || definition.getOptions().isBlank()
					|| java.util.Arrays.stream(definition.getOptions().split(",")).map(String::trim).anyMatch(text::equals);
			case TEXT -> true;
		};
		if (!valid) {
			throw new InvalidRequestException("Custom field '" + definition.getLabel() + "' must be a valid " + definition.getDataType().name().toLowerCase());
		}
	}

	private static boolean isNumber(String text) {
		try {
			Double.parseDouble(text);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean isDate(String text) {
		try {
			java.time.LocalDate.parse(text);
			return true;
		} catch (java.time.format.DateTimeParseException e) {
			return false;
		}
	}

	public Map<String, Object> deserialize(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
	}
}
