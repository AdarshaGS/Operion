package com.operion.student.api;

import java.time.Instant;

import com.operion.student.TransferRequest;

public record TransferRequestResponse(Long id, Long studentId, Long fromCampusId, Long toCampusId, String reason, String status,
		Long requestedBy, Long decidedBy, Instant decidedAt) {

	public static TransferRequestResponse from(TransferRequest transferRequest) {
		return new TransferRequestResponse(transferRequest.getId(), transferRequest.getStudent().getId(),
				transferRequest.getFromCampus().getId(), transferRequest.getToCampus().getId(), transferRequest.getReason(),
				transferRequest.getStatus().name(), transferRequest.getRequestedBy(), transferRequest.getDecidedBy(),
				transferRequest.getDecidedAt());
	}
}
