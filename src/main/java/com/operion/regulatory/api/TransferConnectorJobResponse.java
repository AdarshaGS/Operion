package com.operion.regulatory.api;

import java.time.Instant;

import com.operion.regulatory.TransferConnectorJob;

public record TransferConnectorJobResponse(Long id, Long studentExitId, Long studentId, String studentName, Long schemaId,
		String schemaCode, String connectorName, String status, String payload, String externalReference,
		String failureReason, Instant submittedAt, Instant createdAt) {

	public static TransferConnectorJobResponse from(TransferConnectorJob job) {
		var student = job.getStudentExit().getStudent();
		var person = student.getPerson();
		String name = (person.getFirstName() + " " + (person.getLastName() == null ? "" : person.getLastName())).trim();
		return new TransferConnectorJobResponse(job.getId(), job.getStudentExit().getId(), student.getId(), name,
				job.getSchema().getId(), job.getSchema().getCode(), job.getConnectorName(), job.getStatus().name(),
				job.getPayload(), job.getExternalReference(), job.getFailureReason(), job.getSubmittedAt(), job.getCreatedAt());
	}
}
