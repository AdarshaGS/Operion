package com.operion.identity.api;

import java.time.Instant;

import com.operion.identity.ProfileChangeRequest;

public record ProfileChangeRequestResponse(Long id, Long personId, String phone, String email, String photoUrl, String status,
		Long requestedBy, Long reviewedBy, Instant reviewedAt) {

	public static ProfileChangeRequestResponse from(ProfileChangeRequest request) {
		return new ProfileChangeRequestResponse(request.getId(), request.getPerson().getId(), request.getPhone(), request.getEmail(),
				request.getPhotoUrl(), request.getStatus().name(), request.getRequestedBy(), request.getReviewedBy(),
				request.getReviewedAt());
	}
}
