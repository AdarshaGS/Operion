package com.operion.timetable.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** A null workload limit clears it (unlimited). */
public record UpdateTimetableSettingsRequest(@NotNull @PositiveOrZero Integer gapMinutes, Integer maxTeacherPeriodsPerDay,
		Integer maxTeacherPeriodsPerWeek) {
}
