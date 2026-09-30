package com.operion.regulatory.api;

/** sourceField: a key from GET /regulatory-export-schemas/source-fields, or custom:&lt;fieldKey&gt;
 * (student schemas only). defaultValue is written when the source value is blank. */
public record RegulatoryExportFieldRequest(String columnHeader, String sourceField, String defaultValue) {
}
