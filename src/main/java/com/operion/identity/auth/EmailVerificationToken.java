package com.operion.identity.auth;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Same shape as PasswordResetToken - see that class's javadoc for the tenant-scoped-despite-
 * a-global-User reasoning, which applies identically here. */
@Getter
@Entity
@Table(name = "email_verification_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken extends TenantScopedEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean consumed;

	public EmailVerificationToken(Long userId, String tokenHash, Instant expiresAt) {
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
