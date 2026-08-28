package com.operion.reporting.api;

public record ReportParameterRequest(String name, String type, String label, int sortOrder) {
}
