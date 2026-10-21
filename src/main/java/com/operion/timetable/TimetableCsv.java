package com.operion.timetable;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * A weekly grid as CSV: one row per period (time range in the first column), one column per
 * weekday. Monday-Friday are always shown; Saturday and Sunday only when they hold a slot.
 * Cells that a spreadsheet would read as a formula are prefixed with an apostrophe.
 */
public final class TimetableCsv {

	private TimetableCsv() {
	}

	public static String grid(List<Period> periods, List<TimetableEntry> entries, Function<TimetableEntry, String> cell) {
		Set<DayOfWeek> days = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
		for (TimetableEntry entry : entries) {
			days.add(entry.getDayOfWeek());
		}
		StringBuilder csv = new StringBuilder("Period,Time");
		for (DayOfWeek day : days) {
			csv.append(',').append(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
		}
		csv.append("\r\n");
		for (Period period : periods) {
			csv.append(escape(period.getLabel())).append(',').append(period.getStartTime()).append('-').append(period.getEndTime());
			for (DayOfWeek day : days) {
				csv.append(',');
				if (!period.isTeaching()) {
					csv.append(escape(period.getLabel()));
					continue;
				}
				entries.stream().filter(e -> e.getDayOfWeek() == day && e.getPeriod().getId().equals(period.getId())).findFirst()
						.ifPresent(e -> csv.append(escape(cell.apply(e))));
			}
			csv.append("\r\n");
		}
		return csv.toString();
	}

	static String escape(String value) {
		if (value == null) {
			return "";
		}
		String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
		return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
				? "\"" + safe.replace("\"", "\"\"") + "\""
				: safe;
	}
}
