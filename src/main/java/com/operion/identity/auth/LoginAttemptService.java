package com.operion.identity.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Database-backed failed-login tracker, so lockout survives a restart and is shared by every
 * instance (the earlier in-memory map silently reset on either). Keyed by organisation +
 * email: identities are per-school, so an attempt against one school's account says nothing
 * about the same email at another school, and rotating slugs can't dodge a lockout because
 * each slug is a different account.
 *
 * Every repository call is its own short transaction (no class-level @Transactional), so a
 * failure is recorded even though the login attempt itself ends in an exception. Concurrent
 * failures for the same account are last-write-wins - a lost increment only ever gives an
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

	public boolean isLocked(Long organisationId, String email) {
		return repository.findById(key(organisationId, email)).map(a -> a.isLockedAt(Instant.now())).orElse(false);
	}

	public void recordFailure(Long organisationId, String email) {
		String key = key(organisationId, email);
		try {
			saveFailure(key, organisationId, email);
		} catch (DataIntegrityViolationException raceOnFirstInsert) {
			saveFailure(key, organisationId, email);
		}
	}

	private void saveFailure(String key, Long organisationId, String email) {
		LoginAttempt attempt = repository.findById(key).orElseGet(() -> new LoginAttempt(key, organisationId, email == null ? "" : email.toLowerCase()));
		attempt.recordFailure(maxFailedAttempts, Instant.now().plus(lockoutDuration));
		repository.save(attempt);
	}

	public void recordSuccess(Long organisationId, String email) {
		repository.deleteById(key(organisationId, email));
	}

	private String key(Long organisationId, String email) {
		return organisationId + ":" + (email == null ? "" : email.toLowerCase());
	}
}
