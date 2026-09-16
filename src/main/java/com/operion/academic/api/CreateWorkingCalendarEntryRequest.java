package com.operion.academic.api;

import java.time.LocalDate;

/** campusId null = applies to every campus. type: "HOLIDAY" or "SPECIAL_WORKING_DAY". */
public record CreateWorkingCalendarEntryRequest(Long academicYearId, Long campusId, LocalDate date, String label, String type) {
}
