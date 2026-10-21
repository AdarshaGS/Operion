package com.operion.timetable.api;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** slotType (TEACHING, BREAK or SPECIAL) wins over the older breakSlot flag when both are sent. */
public record PeriodRequest(@NotBlank String label, @NotNull LocalTime startTime, @NotNull LocalTime endTime, Boolean breakSlot,
		@Pattern(regexp = "TEACHING|BREAK|SPECIAL", message = "must be TEACHING, BREAK or SPECIAL") String slotType) {
}
