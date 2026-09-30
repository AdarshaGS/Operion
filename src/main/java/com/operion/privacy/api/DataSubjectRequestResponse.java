package com.operion.privacy.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.privacy.DataSubjectRequest;

public record DataSubjectRequestResponse(Long id, Long personId, String personName, String requestType, String status,
		String description, LocalDate receivedDate, LocalDate dueDate, boolean overdue, Instant identityVerifiedAt,
		String identityVerificationMethod, Instant resolvedAt, String resolutionNote) {

	public static DataSubjectRequestResponse from(DataSubjectRequest request) {
		var person = request.getPerson();
		String name = (person.getFirstName() + " " + (person.getLastName() == null ? "" : person.getLastName())).trim();
		return new DataSubjectRequestResponse(request.getId(), person.getId(), name, request.getRequestType().name(),
				request.getStatus().name(), request.getDescription(), request.getReceivedDate(), request.getDueDate(),
				request.isOpen() && request.getDueDate().isBefore(LocalDate.now()), request.getIdentityVerifiedAt(),
				request.getIdentityVerificationMethod(), request.getResolvedAt(), request.getResolutionNote());
	}
}
