package com.operion.platform.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates tokens for the platform-admin plane only. Signed with its own
 * secret (app.jwt.platform-secret), deliberately distinct from the org-scoped
 * JwtService's key, so a leaked org JWT secret can never mint a cross-tenant platform
 * token and vice versa - the two auth planes are cryptographically, not just logically,
 * separate. Carries no "org" claim at all, unlike JwtService's tokens.
 */
@Component
public class PlatformJwtService {

	private final SecretKey key;
	private final Duration expiration;

	public PlatformJwtService(@Value("${app.jwt.platform-secret}") String secret,
			@Value("${app.jwt.platform-expiration-minutes}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(expirationMinutes);
	}

	public IssuedToken issue(Long platformAdminId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expiration);
		String token = Jwts.builder()
				.subject(String.valueOf(platformAdminId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
		return new IssuedToken(token, expiresAt);
	}

	public Long decodeToPlatformAdminId(String token) {
		Claims claims;
		try {
			claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		} catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidPlatformTokenException("Invalid or expired platform token", ex);
		}
		return Long.parseLong(claims.getSubject());
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}
}
