package com.operion.calendar;

import java.time.LocalDate;

/** One day that is not a working day: a dated HOLIDAY from the working calendar (label is its
 * name) or a recurring WEEKLY_OFF from the organisation's working-days mask. */
public record NonWorkingDay(LocalDate date, Kind kind, String label) {

	public enum Kind {
		HOLIDAY,
		WEEKLY_OFF
	}
}
