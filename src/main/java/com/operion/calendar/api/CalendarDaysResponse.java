package com.operion.calendar.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.calendar.NonWorkingDay;

/** workingDayCount is the number of working days in [from, to]; nonWorkingDays lists the rest
 * with a reason each, so a UI can say "Wed 14 Jan - Pongal" rather than just "not counted". */
public record CalendarDaysResponse(LocalDate from, LocalDate to, int workingDayCount, List<Day> nonWorkingDays) {

	public record Day(LocalDate date, String kind, String label) {
		static Day from(NonWorkingDay day) {
			return new Day(day.date(), day.kind().name(), day.label());
		}
	}

	public static CalendarDaysResponse of(LocalDate from, LocalDate to, int workingDayCount, List<NonWorkingDay> off) {
		return new CalendarDaysResponse(from, to, workingDayCount, off.stream().map(Day::from).toList());
	}
}
