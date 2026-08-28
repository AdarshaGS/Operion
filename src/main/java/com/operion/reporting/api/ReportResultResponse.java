package com.operion.reporting.api;

import java.util.List;
import java.util.Map;

import com.operion.reporting.ReportExecutionService;

public record ReportResultResponse(List<String> columns, List<Map<String, Object>> rows) {

	public static ReportResultResponse from(ReportExecutionService.ExecutionResult result) {
		return new ReportResultResponse(result.columns(), result.rows());
	}
}
