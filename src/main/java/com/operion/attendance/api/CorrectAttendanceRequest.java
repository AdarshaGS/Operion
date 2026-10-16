package com.operion.attendance.api;

import jakarta.validation.constraints.NotBlank;

public record CorrectAttendanceRequest(@NotBlank String newStatus, @NotBlank String reason) {
}
