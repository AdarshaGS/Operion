package com.operion.regulatory.api;

/** entityType: "STUDENT" | "STAFF". Fixed after creation, like code. */
public record CreateRegulatoryExportSchemaRequest(String code, String name, String description, String entityType) {
}
