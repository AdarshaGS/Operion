package com.operion.attendance.api;

import java.time.Instant;

import com.operion.attendance.AttendanceCorrection;

public record AttendanceCorrectionResponse(
		Long id, String previousStatus, String newStatus, String reason, Long correctedBy, Instant correctedAt) {

	static AttendanceCorrectionResponse from(AttendanceCorrection correction) {
		return new AttendanceCorrectionResponse(correction.getId(), correction.getPreviousStatus().name(),
				correction.getNewStatus().name(), correction.getReason(), correction.getCreatedBy(),
				correction.getCreatedAt());
	}
}
