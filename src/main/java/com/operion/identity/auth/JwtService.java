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
import com.operion.identity.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the JWTs that carry a request's acting user + organisation.
 * Both ids are stored as string claims (not numeric) to sidestep any JSON-number-type
 * quirks across JJWT's swappable serialization backends (this project uses gson, not
 * jackson, to avoid clashing with the app's own Jackson 3.x dependency - see build.gradle).
 *
 * Tokens also carry a "cv" (credential version) claim - the user's User.credentialVersion at
 * issue time, bumped on every password change. JwtAuthenticationInterceptor rejects a token
 * whose version no longer matches the user's, which is what makes a password change/reset
 * cut off access tokens already issued. A token with no "cv" claim (issued before the claim
 * existed) is treated as version 0: it keeps working for a user whose password has never
 * changed since, and is rejected for anyone who has - so deploying this does not log
 * everyone out, yet nobody keeps a pre-change token alive past a password change.
 */
@Component
public class JwtService {

	private static final String CREDENTIAL_VERSION_CLAIM = "cv";

	private final SecretKey key;
	private final Duration expiration;
	/** Only used by the two-argument issue(), for callers that don't already hold the User. */
	private final ObjectProvider<UserRepository> userRepository;

	@Autowired
	public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
			ObjectProvider<UserRepository> userRepository) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(expirationMinutes);
		this.userRepository = userRepository;
	}

	/** Standalone construction (unit tests): two-argument issue() then stamps no version, i.e. 0. */
	public JwtService(String secret, long expirationMinutes) {
		this(secret, expirationMinutes, null);
	}

	/** For callers that don't already hold the User (the invite-claim flows): looks the
	 * credential version up from the tenant-scoped User, so the caller's TenantContext must
	 * already be set - as it is everywhere this is called. Prefer the three-argument form. */
	public IssuedToken issue(Long userId, Long organisationId) {
		long version = 0;
		UserRepository repository = userRepository == null ? null : userRepository.getIfAvailable();
		if (repository != null) {
			version = repository.findById(userId).map(user -> user.getCredentialVersion()).orElse(0L);
		}
		return issue(userId, organisationId, version);
	}

	public IssuedToken issue(Long userId, Long organisationId, long credentialVersion) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(expiration);
		String token = Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("org", String.valueOf(organisationId))
				.claim(CREDENTIAL_VERSION_CLAIM, String.valueOf(credentialVersion))
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
		return new IssuedToken(token, expiresAt);
	}

	public TokenPrincipal decodeToPrincipal(String token) {
		// Parsing the claims is inside the try on purpose: a signed token with a non-numeric
		// subject/org/cv must be a 401 (InvalidTokenException), not a NumberFormatException -> 500.
		// NumberFormatException is an IllegalArgumentException.
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			String credentialVersion = claims.get(CREDENTIAL_VERSION_CLAIM, String.class);
			return new TokenPrincipal(Long.parseLong(claims.getSubject()), Long.parseLong(claims.get("org", String.class)),
					credentialVersion == null ? 0L : Long.parseLong(credentialVersion));
		} catch (JwtException | IllegalArgumentException ex) {
			throw new InvalidTokenException("Invalid or expired token", ex);
		}
	}

	public record IssuedToken(String token, Instant expiresAt) {
	}

	public record TokenPrincipal(Long userId, Long organisationId, long credentialVersion) {
	}
}
