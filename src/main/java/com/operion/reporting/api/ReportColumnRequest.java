package com.operion.reporting.api;

public record ReportColumnRequest(String sourceColumn, String label, int sortOrder) {
}
