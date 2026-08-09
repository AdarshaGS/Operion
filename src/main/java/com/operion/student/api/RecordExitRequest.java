package com.operion.student.api;

import java.time.LocalDate;

public record RecordExitRequest(
		String exitType, LocalDate exitDate, String reason, String destinationSchool, Long initiatedBy) {
}
