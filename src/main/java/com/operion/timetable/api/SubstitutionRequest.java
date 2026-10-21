package com.operion.timetable.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record SubstitutionRequest(@NotNull Long entryId, @NotNull LocalDate date, @NotNull Long substituteTeacherPersonId, String reason) {
}
