package com.operion.timetable.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetTimetableEntryRequest(@NotNull Long subjectId, @NotNull Long teacherPersonId,
		@NotNull @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY", message = "must be a weekday name, e.g. MONDAY") String dayOfWeek,
		@NotNull Long periodId) {
}
