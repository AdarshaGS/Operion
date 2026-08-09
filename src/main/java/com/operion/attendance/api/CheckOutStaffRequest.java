package com.operion.attendance.api;

import java.time.Instant;

public record CheckOutStaffRequest(Instant checkOutTime) {
}
