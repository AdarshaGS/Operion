package com.operion.parent;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A one-time claim token letting a Guardian set their own password and get a real
 * login - insert-only until claimed, same convention as StudentEnrollment/TripLog.
 * tokenHash is bcrypt-hashed through the same PasswordEncoder as User passwords; the
 * raw token exists only in PortalInviteService's issue() response, never stored or
 * logged. No stored EXPIRED status - expiry is checked live against expiresAt at claim
 * time rather than needing a scheduler, same reasoning Attendance's register-locking
 * decision used to avoid one.
 */
@Getter
@Entity
@Table(name = "portal_invites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortalInvite extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PortalInviteStatus status;

	public PortalInvite(Person person, String tokenHash, Instant expiresAt) {
		this.person = person;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.status = PortalInviteStatus.PENDING;
	}

	public void claim() {
		if (status != PortalInviteStatus.PENDING) {
			throw new IllegalStateException("Invite has already been claimed");
		}
		this.status = PortalInviteStatus.CLAIMED;
	}
}
