package com.operion.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal RFC4180-shaped CSV line parser (quoted fields, embedded commas/quotes via "")
 * - no external dependency for this, since bulk import (#28) is the only caller today.
 * Neutral shared package (neither core nor vertical - see ArchitectureBoundaryTest) so
 * any future importer can reuse it without crossing the core/vertical boundary.
 */
public final class CsvUtil {

	private CsvUtil() {
	}

	public static List<String> parseLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						current.append('"');
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					current.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == ',') {
				fields.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		fields.add(current.toString());
		return fields;
	}

	/** A plain decimal such as 42 or -3.5 is a number to a spreadsheet, never a formula, so it keeps its leading minus. */
	private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	/**
	 * Inverse of {@link #parseLine}: quotes any field containing a comma, quote or line break. A field a spreadsheet
	 * would read as a formula (starts with = + - @, tab, CR or LF) is prefixed with an apostrophe so exported
	 * user-supplied text cannot run in Excel/Sheets (CSV injection). Fields are untyped strings here, so the rule is
	 * decided by content: plain decimals are left alone, everything else that starts with a trigger is prefixed -
	 * which includes phone numbers written +91..., an accepted cost of the safe default.
	 */
	public static String formatLine(List<String> fields) {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				line.append(',');
			}
			String field = neutraliseFormula(fields.get(i) == null ? "" : fields.get(i));
			if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
				line.append('"').append(field.replace("\"", "\"\"")).append('"');
			} else {
				line.append(field);
			}
		}
		return line.toString();
	}

	private static String neutraliseFormula(String field) {
		if (field.isEmpty() || "=+-@\t\r\n".indexOf(field.charAt(0)) < 0 || PLAIN_NUMBER.matcher(field).matches()) {
			return field;
		}
		return "'" + field;
	}
}
