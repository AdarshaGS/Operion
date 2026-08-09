package com.operion.identity.auth;

import java.time.Instant;

public record LoginResult(String token, Instant expiresAt, Long userId, Long organisationId) {
}
