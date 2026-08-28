package com.operion.reporting.api;

import java.util.List;

/** Shared shape for both create and update - a report's definition is the same fields either way. */
public record SaveReportRequest(String name, String description, String sqlQuery, List<ReportParameterRequest> parameters,
		List<ReportColumnRequest> columns) {
}
