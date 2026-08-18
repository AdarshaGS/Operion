package com.operion.identity.auth;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One-time-use (rotated on every refresh) - same tokenHash/never-store-raw convention as
 * PortalInvite. Tenant-scoped like PortalInvite too: the refresh call itself resolves the
 * org from the request body first (no valid access token to carry it - that's the whole
 * point of this endpoint), then sets TenantContext before touching this table.
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends TenantScopedEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revoked = false;
	}

	public void revoke() {
		this.revoked = true;
	}

	public boolean isValid() {
		return !revoked && expiresAt.isAfter(Instant.now());
	}
}
