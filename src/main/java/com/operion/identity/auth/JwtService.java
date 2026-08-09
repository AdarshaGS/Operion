package com.operion.identity.auth;

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
 * Issues and validates the JWTs that carry a request's acting user + organisation.
 * Both ids are stored as string claims (not numeric) to sidestep any JSON-number-type
 * quirks across JJWT's swappable serialization backends (this project uses gson, not
 * jackson, to avoid clashing with the app's own Jackson 3.x dependency - see build.gradle).
 */
@Component
public class JwtService {

	private final SecretKey key;
	private final Duration expiration;

	public JwtService(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(expirationMinutes);
	}

	public IssuedToken issue(Long userId, Long organisationId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expiration);
		String token = Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("org", String.valueOf(organisationId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
		return new IssuedToken(token, expiresAt);
	}

	public TokenPrincipal decodeToPrincipal(String token) {
		Claims claims;
		try {
			claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		} catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException("Invalid or expired token", ex);
		}
		return new TokenPrincipal(Long.parseLong(claims.getSubject()), Long.parseLong(claims.get("org", String.class)));
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

	public record TokenPrincipal(Long userId, Long organisationId) {
	}
}
