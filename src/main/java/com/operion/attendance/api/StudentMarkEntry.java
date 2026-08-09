package com.operion.attendance.api;

public record StudentMarkEntry(Long studentEnrollmentId, String status, boolean excused, String remarks) {
}
