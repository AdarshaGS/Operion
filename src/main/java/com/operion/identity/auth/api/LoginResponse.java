package com.operion.identity.auth.api;

import java.time.Instant;

import com.operion.identity.auth.LoginResult;

public record LoginResponse(String token, Instant expiresAt, Long userId, Long organisationId) {

	static LoginResponse from(LoginResult result) {
		return new LoginResponse(result.token(), result.expiresAt(), result.userId(), result.organisationId());
	}
}
