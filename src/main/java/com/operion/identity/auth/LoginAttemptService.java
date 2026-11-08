package com.operion.identity.auth;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

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
 *
 * The key has to agree with how the database resolves a login email, or lockout can be dodged.
 * The users table uses an accent- and case-insensitive collation, so "jose@x.com" and
 * "jos\u00e9@x.com" are the same account to the lookup but different strings to a Java map key.
 * Hence two key shapes: once login has resolved a user, failures are counted against that
 * user's id (every spelling the database accepts shares one counter); while no user resolves,
 * they are counted against the email accent-stripped and lower-cased, so varying accents cannot
 * mint a fresh counter per attempt. The same table also carries the per-user "change password"
 * guess counter (see isPasswordChangeLocked), under a key that cannot collide with a login key.
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

	/** Email-keyed check, for a login whose email did not resolve to a user. */
	public boolean isLocked(Long organisationId, String email) {
		return isLockedKey(emailKey(organisationId, email));
	}

	public void recordFailure(Long organisationId, String email) {
		recordFailureKey(emailKey(organisationId, email), organisationId, normaliseEmail(email));
	}

	public void recordSuccess(Long organisationId, String email) {
		repository.deleteById(emailKey(organisationId, email));
	}

	/** User-keyed check, for a login whose email resolved to a user. */
	public boolean isLockedForUser(Long organisationId, Long userId) {
		return isLockedKey(userKey(organisationId, userId));
	}

	public void recordFailureForUser(Long organisationId, Long userId) {
		recordFailureKey(userKey(organisationId, userId), organisationId, "user:" + userId);
	}

	public void recordSuccessForUser(Long organisationId, Long userId) {
		repository.deleteById(userKey(organisationId, userId));
	}

	/** Guess limiter for the authenticated change-password endpoint: a stolen access token must
	 * not be a free oracle for the account's current password. Separate counter from login's. */
	public boolean isPasswordChangeLocked(Long organisationId, Long userId) {
		return isLockedKey(passwordChangeKey(organisationId, userId));
	}

	public void recordPasswordChangeFailure(Long organisationId, Long userId) {
		recordFailureKey(passwordChangeKey(organisationId, userId), organisationId, "pwchange:" + userId);
	}

	public void recordPasswordChangeSuccess(Long organisationId, Long userId) {
		repository.deleteById(passwordChangeKey(organisationId, userId));
	}

	/** Lower-cased and accent-stripped (NFD, combining marks removed), so spellings the database's
	 * accent/case-insensitive collation treats as one email map to one string. */
	static String normaliseEmail(String email) {
		if (email == null) {
			return "";
		}
		String decomposed = Normalizer.normalize(email.trim(), Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}

	private boolean isLockedKey(String key) {
		return repository.findById(key).map(a -> a.isLockedAt(Instant.now())).orElse(false);
	}

	private void recordFailureKey(String key, Long organisationId, String label) {
		try {
			saveFailure(key, organisationId, label);
		} catch (DataIntegrityViolationException raceOnFirstInsert) {
			saveFailure(key, organisationId, label);
		}
	}

	private void saveFailure(String key, Long organisationId, String label) {
		LoginAttempt attempt = repository.findById(key).orElseGet(() -> new LoginAttempt(key, organisationId, label));
		attempt.recordFailure(maxFailedAttempts, Instant.now().plus(lockoutDuration));
		repository.save(attempt);
	}

	// The email part is hashed so the key has a fixed length however long the email is
	// (attempt_key is a default-length varchar) and so one org's key space can't be confused with another shape.
	private static String emailKey(Long organisationId, String email) {
		return organisationId + ":e:" + TokenHasher.sha256Hex(normaliseEmail(email));
	}

	private static String userKey(Long organisationId, Long userId) {
		return organisationId + ":u:" + userId;
	}

	private static String passwordChangeKey(Long organisationId, Long userId) {
		return organisationId + ":pw:" + userId;
	}
}
