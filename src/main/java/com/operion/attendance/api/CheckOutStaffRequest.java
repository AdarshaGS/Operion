package com.operion.attendance.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record CheckOutStaffRequest(@NotNull Instant checkOutTime) {
}
