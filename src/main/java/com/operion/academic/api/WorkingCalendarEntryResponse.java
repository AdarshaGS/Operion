package com.operion.academic.api;

import java.time.LocalDate;

import com.operion.academic.WorkingCalendarEntry;

public record WorkingCalendarEntryResponse(
		Long id, Long academicYearId, Long campusId, LocalDate date, String label, String type) {

	static WorkingCalendarEntryResponse from(WorkingCalendarEntry entry) {
		return new WorkingCalendarEntryResponse(entry.getId(), entry.getAcademicYear().getId(),
				entry.getCampus() != null ? entry.getCampus().getId() : null, entry.getDate(), entry.getLabel(),
				entry.getType().name());
	}
}
