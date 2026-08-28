package com.operion.reporting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.operion.audit.AuditLogService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs a SavedReport's SQL over the restricted reportingDataSource (V55's SELECT-only
 * `reporting_ro` role, granted per-view on the `reporting_*` views, V58) - never the app's own
 * TenantScopedEntity-backed tables. Layered defence per GitHub #187: SqlGuard rejects
 * anything but a single SELECT before this even opens a connection (1); the DB role
 * itself is the real boundary (2); filter values are always bound as JDBC parameters,
 * never string-substituted (3); a query timeout (4) and row cap (5) bound a runaway query.
 */
@Service
public class ReportExecutionService {

	private static final int ROW_LIMIT = 5000;
	private static final int QUERY_TIMEOUT_SECONDS = 10;
	private static final Pattern NAMED_PARAMETER = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

	private final DataSource reportingDataSource;
	private final SavedReportParameterRepository parameterRepository;
	private final AuditLogService auditLogService;

	public ReportExecutionService(@Qualifier("reportingDataSource") DataSource reportingDataSource,
			SavedReportParameterRepository parameterRepository, AuditLogService auditLogService) {
		this.reportingDataSource = reportingDataSource;
		this.parameterRepository = parameterRepository;
		this.auditLogService = auditLogService;
	}

	public record ExecutionResult(List<String> columns, List<Map<String, Object>> rows) {
	}

	public ExecutionResult run(SavedReport report, Map<String, Object> parameterValues, boolean export) {
		SqlGuard.assertSingleSelect(report.getSqlQuery());
		validateParameterNames(report, parameterValues);

		List<String> orderedParamNames = new ArrayList<>();
		String positionalSql = rewriteNamedParameters(report.getSqlQuery(), orderedParamNames);

		try (Connection connection = reportingDataSource.getConnection()) {
			try (PreparedStatement setOrg = connection.prepareStatement("SET @reporting_org_id = ?")) {
				setOrg.setLong(1, report.getOrganisationId());
				setOrg.execute();
			}

			ExecutionResult result;
			try (PreparedStatement statement = connection.prepareStatement(positionalSql)) {
				statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
				statement.setMaxRows(ROW_LIMIT);
				for (int i = 0; i < orderedParamNames.size(); i++) {
					statement.setObject(i + 1, parameterValues.get(orderedParamNames.get(i)));
				}
				try (ResultSet resultSet = statement.executeQuery()) {
					result = readResults(resultSet);
				}
			}

			auditLogService.record("SavedReport", report.getId(), export ? "EXPORTED" : "RUN", null,
					Map.of("parameters", parameterValues, "rowCount", result.rows().size()));
			return result;
		} catch (SQLException e) {
			throw new ReportExecutionException("Report query failed: " + e.getMessage(), e);
		}
	}

	private ExecutionResult readResults(ResultSet resultSet) throws SQLException {
		List<String> columns = new ArrayList<>();
		ResultSetMetaData metadata = resultSet.getMetaData();
		int columnCount = metadata.getColumnCount();
		for (int i = 1; i <= columnCount; i++) {
			columns.add(metadata.getColumnLabel(i));
		}
		List<Map<String, Object>> rows = new ArrayList<>();
		while (resultSet.next()) {
			Map<String, Object> row = new LinkedHashMap<>();
			for (int i = 1; i <= columnCount; i++) {
				row.put(metadata.getColumnLabel(i), resultSet.getObject(i));
			}
			rows.add(row);
		}
		return new ExecutionResult(columns, rows);
	}

	private void validateParameterNames(SavedReport report, Map<String, Object> parameterValues) {
		List<String> required = parameterRepository.findBySavedReportIdOrderBySortOrder(report.getId()).stream()
				.map(SavedReportParameter::getName).toList();
		for (String name : required) {
			if (!parameterValues.containsKey(name)) {
				throw new IllegalArgumentException("Missing value for report parameter: " + name);
			}
		}
	}

	private String rewriteNamedParameters(String sql, List<String> orderedParamNames) {
		Matcher matcher = NAMED_PARAMETER.matcher(sql);
		StringBuilder result = new StringBuilder();
		int lastEnd = 0;
		while (matcher.find()) {
			result.append(sql, lastEnd, matcher.start()).append('?');
			orderedParamNames.add(matcher.group(1));
			lastEnd = matcher.end();
		}
		result.append(sql.substring(lastEnd));
		return result.toString();
	}
}
