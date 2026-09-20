package com.operion.identity.api;

import java.time.Instant;

import com.operion.identity.User;

public record UserResponse(Long id, String email, String phone, String status, Instant lastLoginAt, Instant invitedAt) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getPhone(), user.getStatus().name(), user.getLastLoginAt(),
				user.getCreatedAt());
	}
}
