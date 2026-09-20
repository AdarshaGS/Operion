package com.operion.customfield.api;

/** dataType: "TEXT" | "NUMBER" | "DATE" | "BOOLEAN" | "SELECT". options: comma-separated,
 * only meaningful when dataType is SELECT. */
public record CreateCustomFieldDefinitionRequest(
		String entityType, String fieldKey, String label, String dataType, String options, boolean required) {
}
