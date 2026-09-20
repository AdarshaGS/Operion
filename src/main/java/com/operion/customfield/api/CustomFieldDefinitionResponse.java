package com.operion.customfield.api;

import com.operion.customfield.CustomFieldDefinition;

public record CustomFieldDefinitionResponse(
		Long id, String entityType, String fieldKey, String label, String dataType, String options, boolean required, String status) {

	public static CustomFieldDefinitionResponse from(CustomFieldDefinition definition) {
		return new CustomFieldDefinitionResponse(definition.getId(), definition.getEntityType(), definition.getFieldKey(),
				definition.getLabel(), definition.getDataType().name(), definition.getOptions(), definition.isRequired(),
				definition.getStatus().name());
	}
}
