package com.operion.identity.auth;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Staff analogue of com.operion.parent.PortalInvite, pointed at an already-existing User
 * "login shell" (userId) rather than a Person - see StaffInviteService for why staff
 * onboarding can create the User row eagerly (unlike the guardian flow, which defers it
 * until claim). Same insert-only-until-claimed, live-expiry-check, bcrypt-hashed-token
 * conventions as PortalInvite.
 */
@Getter
@Entity
@Table(name = "staff_invites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffInvite extends TenantScopedEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StaffInviteStatus status;

	public StaffInvite(Long userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.status = StaffInviteStatus.PENDING;
	}

	public void claim() {
		if (status != StaffInviteStatus.PENDING) {
			throw new IllegalStateException("Invite has already been claimed");
		}
		this.status = StaffInviteStatus.CLAIMED;
	}
}
