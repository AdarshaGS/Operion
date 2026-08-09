package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.StudentExit;

public record StudentExitResponse(
		Long id, Long studentId, String exitType, LocalDate exitDate, String reason, String destinationSchool,
		Long initiatedBy) {

	static StudentExitResponse from(StudentExit exit) {
		return new StudentExitResponse(exit.getId(), exit.getStudent().getId(), exit.getExitType().name(),
				exit.getExitDate(), exit.getReason(), exit.getDestinationSchool(), exit.getInitiatedBy());
	}
}
