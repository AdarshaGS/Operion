package com.operion.reporting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * First layer of defence against a report's SQL doing anything other than a single read
 * (GitHub #187, point 1) - NOT the actual security boundary on its own, since string-based
 * SQL validation is bypassable in isolation. The real boundary is the restricted, SELECT-
 * only `reporting_ro` DB role (V55) that ReportExecutionService connects as - even a bug
 * here cannot reach real data or perform a write, because that DB user physically cannot.
 * Applied both at author-time (SavedReportService) and at every execution (defence in
 * depth against a row edited directly in the DB, bypassing the app entirely).
 */
final class SqlGuard {

	private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
			"\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|REPLACE|CALL|EXEC|EXECUTE|MERGE|SET|LOCK|UNLOCK)\\b",
			Pattern.CASE_INSENSITIVE);

	private SqlGuard() {
	}

	static void assertSingleSelect(String sql) {
		String trimmed = sql == null ? "" : sql.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Report SQL query must not be empty");
		}
		String body = trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
		if (body.contains(";")) {
			throw new IllegalArgumentException("Report SQL must be a single statement");
		}
		if (body.contains("--") || body.contains("/*")) {
			throw new IllegalArgumentException("Report SQL must not contain comments");
		}
		if (!body.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
			throw new IllegalArgumentException("Report SQL must be a SELECT statement");
		}
		if (FORBIDDEN_KEYWORDS.matcher(body).find()) {
			throw new IllegalArgumentException("Report SQL must not contain data-modifying statements");
		}
	}
}
