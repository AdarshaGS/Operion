package com.operion.customfield.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** dataType: "TEXT" | "NUMBER" | "DATE" | "BOOLEAN" | "SELECT". options: comma-separated,
 * only meaningful (and then required) when dataType is SELECT. fieldKey becomes a JSON key on every record,
 * so it is lower-case letters, digits and underscores only. */
public record CreateCustomFieldDefinitionRequest(
		@NotBlank String entityType,
		@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]*", message = "fieldKey must be lower-case letters, digits and underscores, starting with a letter") String fieldKey,
		@NotBlank String label, @NotBlank String dataType, String options, boolean required) {
}
