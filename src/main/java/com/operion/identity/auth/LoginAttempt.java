package com.operion.identity.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Failed-login counter for one (organisation, lower-cased email) pair. Not a
 * TenantScopedEntity: it is written on the failure path before any session-wide tenant is
 * guaranteed, so organisation_id is an explicit part of the key - see {@link LoginAttemptService}. */
@Getter
@Entity
@Table(name = "login_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

	@Id
	@Column(name = "attempt_key", nullable = false)
	private String key;

	@Column(name = "organisation_id", nullable = false)
	private Long organisationId;

	@Column(nullable = false)
	private String email;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public LoginAttempt(String key, Long organisationId, String email) {
		this.key = key;
		this.organisationId = organisationId;
		this.email = email;
	}

	public boolean isLockedAt(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/** Counts a failure; on reaching the limit, locks until {@code lockedUntil} and resets the counter. */
	public void recordFailure(int maxFailedAttempts, Instant lockedUntil) {
		failedCount++;
		if (failedCount >= maxFailedAttempts) {
			this.lockedUntil = lockedUntil;
			failedCount = 0;
		}
		updatedAt = Instant.now();
	}
}
