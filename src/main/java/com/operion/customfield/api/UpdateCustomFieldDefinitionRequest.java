package com.operion.customfield.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateCustomFieldDefinitionRequest(@NotBlank String label, @NotBlank String dataType, String options, boolean required) {
}
