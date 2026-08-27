package com.operion.identity.auth.api;

import java.time.Instant;

import com.operion.identity.auth.LoginResult;

public record LoginResponse(String token, Instant expiresAt, String refreshToken, Instant refreshExpiresAt, Long userId,
		Long organisationId) {

	public static LoginResponse from(LoginResult result) {
		return new LoginResponse(result.token(), result.expiresAt(), result.refreshToken(), result.refreshExpiresAt(),
				result.userId(), result.organisationId());
	}
}
