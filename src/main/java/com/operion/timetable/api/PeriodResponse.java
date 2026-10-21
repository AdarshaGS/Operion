package com.operion.timetable.api;

import java.time.LocalTime;

import com.operion.timetable.Period;

/** breakSlot is true for every non-teaching slot (BREAK and SPECIAL); slotType tells them apart. templateId is null for the default school day. */
public record PeriodResponse(Long id, int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot,
		String slotType, Long templateId, String templateName) {

	static PeriodResponse from(Period period) {
		var template = period.getTemplate();
		return new PeriodResponse(period.getId(), period.getOrdinal(), period.getLabel(), period.getStartTime(),
				period.getEndTime(), period.isBreakSlot(), period.getSlotType().name(),
				template == null ? null : template.getId(), template == null ? null : template.getName());
	}
}
