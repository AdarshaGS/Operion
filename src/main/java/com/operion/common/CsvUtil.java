package com.operion.common;

import java.util.ArrayList;
import java.util.List;

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
}
