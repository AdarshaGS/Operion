package com.operion.timetable.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** periodId is optional; leaving it out blocks the teacher for the whole weekday. */
public record UnavailabilityRequest(
		@NotNull @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY", message = "must be a weekday name, e.g. MONDAY") String dayOfWeek,
		Long periodId, String reason) {
}
