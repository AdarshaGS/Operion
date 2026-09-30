package com.operion.identity.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Failed-login counter for one (lower-cased) email. Deliberately not tenant-scoped - see
 * {@link LoginAttemptService}. */
@Getter
@Entity
@Table(name = "login_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

	@Id
	@Column(nullable = false)
	private String email;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public LoginAttempt(String email) {
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
