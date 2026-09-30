package com.operion.identity.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Database-backed failed-login tracker, so lockout survives a restart and is shared by every
 * instance (the earlier in-memory map silently reset on either). Keyed by email alone, not
 * organisation+email - User is global (see User's own javadoc), so the same password attempt
 * is valid against any org slug the attacker tries, and keying by org would let them dodge
 * the lockout just by rotating slugs.
 *
 * Every repository call is its own short transaction (no class-level @Transactional), so a
 * failure is recorded even though the login attempt itself ends in an exception. Concurrent
 * failures for the same email are last-write-wins - a lost increment only ever gives an
 * attacker one extra guess, acceptable next to the cost of row locking on the login path.
 */
@Service
public class LoginAttemptService {

	private final LoginAttemptRepository repository;
	private final int maxFailedAttempts;
	private final Duration lockoutDuration;

	public LoginAttemptService(LoginAttemptRepository repository, @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
			@Value("${app.auth.lockout-duration-minutes:15}") long lockoutDurationMinutes) {
		this.repository = repository;
		this.maxFailedAttempts = maxFailedAttempts;
		this.lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes);
	}

	public boolean isLocked(String email) {
		return repository.findById(key(email)).map(a -> a.isLockedAt(Instant.now())).orElse(false);
	}

	public void recordFailure(String email) {
		String key = key(email);
		try {
			saveFailure(key);
		} catch (DataIntegrityViolationException raceOnFirstInsert) {
			saveFailure(key);
		}
	}

	private void saveFailure(String key) {
		LoginAttempt attempt = repository.findById(key).orElseGet(() -> new LoginAttempt(key));
		attempt.recordFailure(maxFailedAttempts, Instant.now().plus(lockoutDuration));
		repository.save(attempt);
	}

	public void recordSuccess(String email) {
		repository.deleteById(key(email));
	}

	private String key(String email) {
		return email == null ? "" : email.toLowerCase();
	}
}
