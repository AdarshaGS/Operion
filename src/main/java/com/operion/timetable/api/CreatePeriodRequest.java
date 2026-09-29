package com.operion.timetable.api;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePeriodRequest(@NotNull Integer ordinal, @NotBlank String label, @NotNull LocalTime startTime,
		@NotNull LocalTime endTime, Boolean breakSlot) {
}
