package com.operion.customfield.api;

public record UpdateCustomFieldDefinitionRequest(String label, String dataType, String options, boolean required) {
}
