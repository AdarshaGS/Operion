package com.operion.reporting.api;

import java.util.List;

import com.operion.reporting.SavedReport;
import com.operion.reporting.SavedReportColumn;
import com.operion.reporting.SavedReportParameter;

public record SavedReportResponse(Long id, String name, String description, String sqlQuery, String status, Long createdBy,
		List<ReportParameterRequest> parameters, List<ReportColumnRequest> columns) {

	public static SavedReportResponse from(SavedReport report, List<SavedReportParameter> parameters, List<SavedReportColumn> columns) {
		return new SavedReportResponse(report.getId(), report.getName(), report.getDescription(), report.getSqlQuery(),
				report.getStatus().name(), report.getCreatedBy(),
				parameters.stream().map(p -> new ReportParameterRequest(p.getName(), p.getType().name(), p.getLabel(), p.getSortOrder())).toList(),
				columns.stream().map(c -> new ReportColumnRequest(c.getSourceColumn(), c.getLabel(), c.getSortOrder())).toList());
	}
}
