package com.operion.identity.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and rotates refresh tokens - same raw-token-shown-once, bcrypt-hash-stored
 * convention as PortalInviteService's invite tokens. Rotation (the old token is revoked
 * the moment a new pair is issued from it) means a leaked-and-replayed refresh token is
 * usable exactly once before the legitimate client's next refresh invalidates it.
 */
@Service
public class RefreshTokenService {

	private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(30);

	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** Caller must already have TenantContext set (from login()'s or refresh()'s own
	 * org-slug resolution) - this entity is tenant-scoped like every other. */
	public IssuedRefreshToken issue(Long userId) {
		String rawToken = generateToken();
		RefreshToken token = refreshTokenRepository
				.save(new RefreshToken(userId, passwordEncoder.encode(rawToken), Instant.now().plus(REFRESH_TOKEN_VALIDITY)));
		return new IssuedRefreshToken(rawToken, token.getExpiresAt());
	}

	/** Finds the still-valid token matching the given raw value and revokes it in the same
	 * step (rotation) - within the org TenantContext already scopes queries to. Empty if no
	 * match, expired, or already used/revoked; the caller treats all three identically. */
	public Optional<Long> consumeAndRevoke(String rawToken) {
		return refreshTokenRepository.findByRevokedFalse().stream()
				.filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
				.findFirst()
				.filter(RefreshToken::isValid)
				.map(token -> {
					token.revoke();
					refreshTokenRepository.save(token);
					return token.getUserId();
				});
	}

	/** "Sign out everywhere" - revokes every still-active refresh token for this user, not
	 * just one. The frontend never stores the refresh token it's issued (only the access
	 * token), so logout has no single token to target and instead invalidates all of them,
	 * under the TenantContext the caller's own bearer token already resolved. */
	public void revokeAllForUser(Long userId) {
		refreshTokenRepository.findByUserIdAndRevokedFalse(userId).forEach(token -> {
			token.revoke();
			refreshTokenRepository.save(token);
		});
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
	}
}
