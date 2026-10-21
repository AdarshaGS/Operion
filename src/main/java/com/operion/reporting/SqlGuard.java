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
			"\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|REPLACE(?!\\s*\\()|CALL|EXEC|EXECUTE|MERGE|SET|LOCK|UNLOCK)\\b",
			Pattern.CASE_INSENSITIVE);

	/** Quoted strings and backticked identifiers, removed before the keyword checks so 'Update me', 'a@b.com' or a column
	 * aliased `set` stay allowed. A backslash is deliberately not treated as an escape: if it were, 'x\' could make the
	 * guard think real SQL after it is still inside the string. Worst case is refusing O\'Brien - write O''Brien. */
	private static final Pattern QUOTED = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"|`[^`]*`");

	/** The reporting views filter rows by the session variable @reporting_org_id, and reporting_ro may read every row of
	 * every view - so a query that can assign a user variable (inline {@code @v := ...} or {@code SELECT ... INTO @v})
	 * can switch itself to another school. Any user variable, := or INTO is refused outright; # is MySQL's other comment. */
	private static final Pattern VARIABLE_OR_ASSIGNMENT = Pattern.compile("@|:=|#|\\bINTO\\b", Pattern.CASE_INSENSITIVE);

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
		String unquoted = QUOTED.matcher(body).replaceAll("''");
		if (FORBIDDEN_KEYWORDS.matcher(unquoted).find()) {
			throw new IllegalArgumentException("Report SQL must not contain data-modifying statements");
		}
		if (VARIABLE_OR_ASSIGNMENT.matcher(unquoted).find()) {
			throw new IllegalArgumentException("Report SQL must not use variables, assignments (:=), INTO or # comments");
		}
	}
}
