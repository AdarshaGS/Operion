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
 *
 * <p>The one thing the DB role cannot stop is a report re-pointing the session variable
 * {@code @reporting_org_id} that every reporting view filters on (SEC-H3): reporting_ro may
 * read every row of every view, so a query that can assign that variable reads another
 * school's data. This guard is therefore an allow-list, not a deny-list:
 * <ul>
 *   <li>the text must be one statement that starts with SELECT or WITH;</li>
 *   <li>a backslash, control characters and the name {@code reporting_org_id} are refused anywhere in the raw
 *   text, literals included. MySQL treats a backslash as an escape inside '...' and "...", so any parser that
 *   disagrees with it about where a literal ends can be desynchronised (the {@code '\''} trick) and made to see
 *   an assignment as string content. With backslashes gone, the only escape left is the doubled quote, which this
 *   guard and MySQL read identically, so literal contents can be masked and are proven inert;</li>
 *   <li>outside literals, every way of writing a user variable, an assignment, a comment (including MySQL's
 *   executable and optimizer-hint block comments), a second
 *   statement or a JDBC escape ({@code {call ...}}) is refused;</li>
 *   <li>what remains is checked for write/DDL/locking/file/sleep keywords and functions.</li>
 * </ul>
 */
final class SqlGuard {

	/** Generous for hand-written reports, small enough that a pathological body cannot burn CPU in the checks. */
	private static final int MAX_LENGTH = 20_000;

	private static final String ORG_VARIABLE_NAME = "reporting_org_id";

	/** Checked against the text with all literals and quoted identifiers masked out, so 'Update me', 'a@b.com' or a column
	 * aliased `set` stay allowed. */
	private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
			"\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|REPLACE(?!\\s*\\()|CALL|EXEC|EXECUTE|MERGE|SET|LOCK|UNLOCK"
					+ "|LOAD|HANDLER|PREPARE|DEALLOCATE|RENAME|PROCEDURE|INTO|OUTFILE|DUMPFILE|LOAD_FILE"
					+ "|SLEEP|BENCHMARK|GET_LOCK|RELEASE_LOCK|RELEASE_ALL_LOCKS|IS_FREE_LOCK|IS_USED_LOCK"
					+ "|MASTER_POS_WAIT|SOURCE_POS_WAIT|WAIT_FOR_EXECUTED_GTID_SET)\\b"
					+ "|\\bFOR\\s+SHARE\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern STARTS_WITH_SELECT_OR_WITH = Pattern.compile("^(?:SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

	private SqlGuard() {
	}

	static void assertSingleSelect(String sql) {
		String trimmed = trimAscii(sql == null ? "" : sql);
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Report SQL query must not be empty");
		}
		if (trimmed.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("Report SQL is too long");
		}
		assertSafeCharacters(trimmed);

		String body = trimmed.endsWith(";") ? trimAscii(trimmed.substring(0, trimmed.length() - 1)) : trimmed;
		String masked = maskLiteralsAndRejectUnsafeSyntax(body);

		if (!STARTS_WITH_SELECT_OR_WITH.matcher(masked).find()) {
			throw new IllegalArgumentException("Report SQL must be a SELECT statement");
		}
		if (FORBIDDEN_KEYWORDS.matcher(masked).find()) {
			throw new IllegalArgumentException("Report SQL must not contain data-modifying, locking or file statements");
		}
	}

	/** Refused everywhere, literals included - see the class comment. */
	private static void assertSafeCharacters(String sql) {
		if (sql.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("Report SQL must not contain backslashes (write O''Brien, not O\\'Brien)");
		}
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0x7f) {
				throw new IllegalArgumentException("Report SQL must not contain control characters");
			}
		}
		if (sql.toLowerCase(Locale.ROOT).contains(ORG_VARIABLE_NAME)) {
			throw new IllegalArgumentException("Report SQL must not reference the organisation scope variable");
		}
	}

	/**
	 * Walks the SQL the way MySQL does (quote characters ', " and ` each end at the next unpaired same quote; a doubled
	 * quote is the only escape, backslashes having been refused already), replaces every quoted run with {@code ''} and
	 * rejects every construct outside a quoted run that could assign a variable, hide text, or start another statement.
	 */
	private static String maskLiteralsAndRejectUnsafeSyntax(String body) {
		StringBuilder masked = new StringBuilder(body.length());
		int length = body.length();
		int i = 0;
		while (i < length) {
			char c = body.charAt(i);
			if (c == '\'' || c == '"' || c == '`') {
				i = endOfQuotedRun(body, i, c);
				masked.append("''");
				continue;
			}
			char next = i + 1 < length ? body.charAt(i + 1) : '\0';
			if (c == ';') {
				throw new IllegalArgumentException("Report SQL must be a single statement");
			}
			if (c == '#' || (c == '-' && next == '-') || (c == '/' && next == '*') || (c == '*' && next == '/')) {
				throw new IllegalArgumentException("Report SQL must not contain comments");
			}
			if (c == '@' || (c == ':' && next == '=')) {
				throw new IllegalArgumentException("Report SQL must not use variables or assignments (:=)");
			}
			if (c == '{' || c == '}') {
				throw new IllegalArgumentException("Report SQL must not contain JDBC escape sequences");
			}
			masked.append(c);
			i++;
		}
		return masked.toString();
	}

	/** Index just past the closing quote of the run opened at {@code open}; a doubled quote stays inside the run. */
	private static int endOfQuotedRun(String body, int open, char quote) {
		int i = open + 1;
		while (i < body.length()) {
			if (body.charAt(i) == quote) {
				if (i + 1 < body.length() && body.charAt(i + 1) == quote) {
					i += 2;
					continue;
				}
				return i + 1;
			}
			i++;
		}
		throw new IllegalArgumentException("Report SQL has an unterminated quote");
	}

	/** Trims only the whitespace MySQL itself skips; String.strip() would also drop Unicode spaces MySQL rejects. */
	private static String trimAscii(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && isAsciiWhitespace(s.charAt(start))) {
			start++;
		}
		while (end > start && isAsciiWhitespace(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(start, end);
	}

	private static boolean isAsciiWhitespace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}
}
