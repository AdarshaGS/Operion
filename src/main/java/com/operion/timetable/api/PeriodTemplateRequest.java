package com.operion.timetable.api;

import jakarta.validation.constraints.NotBlank;

public record PeriodTemplateRequest(@NotBlank String name) {
}
