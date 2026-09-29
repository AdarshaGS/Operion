package com.operion.timetable.api;

import java.time.LocalTime;

import com.operion.timetable.Period;

public record PeriodResponse(Long id, int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {

	static PeriodResponse from(Period period) {
		return new PeriodResponse(period.getId(), period.getOrdinal(), period.getLabel(), period.getStartTime(),
				period.getEndTime(), period.isBreakSlot());
	}
}
