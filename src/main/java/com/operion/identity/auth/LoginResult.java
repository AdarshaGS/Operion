package com.operion.identity.auth;

import java.time.Instant;

public record LoginResult(String token, Instant expiresAt, String refreshToken, Instant refreshExpiresAt, Long userId,
		Long organisationId) {
}
