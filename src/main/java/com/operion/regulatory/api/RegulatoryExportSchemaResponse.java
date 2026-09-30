package com.operion.regulatory.api;

import java.util.List;

import com.operion.regulatory.RegulatoryExportField;
import com.operion.regulatory.RegulatoryExportSchema;

public record RegulatoryExportSchemaResponse(Long id, String code, String name, String description, String entityType,
		String status, List<Field> fields) {

	public record Field(Long id, int ordinal, String columnHeader, String sourceField, String defaultValue) {

		static Field from(RegulatoryExportField field) {
			return new Field(field.getId(), field.getOrdinal(), field.getColumnHeader(), field.getSourceField(),
					field.getDefaultValue());
		}
	}

	public static RegulatoryExportSchemaResponse from(RegulatoryExportSchema schema, List<RegulatoryExportField> fields) {
		return new RegulatoryExportSchemaResponse(schema.getId(), schema.getCode(), schema.getName(), schema.getDescription(),
				schema.getEntityType().name(), schema.getStatus().name(), fields.stream().map(Field::from).toList());
	}
}
