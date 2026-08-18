package com.operion.identity.auth;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Same tokenHash/never-store-raw, tenant-scoped-despite-a-global-User shape as
 * RefreshToken - see RefreshToken's own javadoc for why a global User still needs a
 * TenantContext to save one of these (the request that creates it always has an org slug
 * to resolve first, either from the login page's forgot-password form or the bearer token). */
@Getter
@Entity
@Table(name = "password_reset_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends TenantScopedEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean consumed;

	public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.consumed = false;
	}

	public void consume() {
		this.consumed = true;
	}

	public boolean isValid() {
		return !consumed && expiresAt.isAfter(Instant.now());
	}
}
