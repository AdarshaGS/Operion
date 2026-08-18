package com.operion.identity.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Single-instance in-memory failed-login tracker - same "v1 simplification" trade-off as
 * RazorpayCredentialsProvider's one shared account (documented there, not hidden): a
 * multi-instance deployment would need this moved to Redis/DB instead of a ConcurrentHashMap,
 * since lockout state here doesn't survive a restart or get shared across instances.
 *
 * Keyed by email alone, not organisation+email - User is global (see User's own javadoc), so
 * the same password attempt is valid against any org slug the attacker tries, and keying by
 * org would let them dodge the lockout just by rotating slugs.
 */
@Service
public class LoginAttemptService {

	private final int maxFailedAttempts;
	private final Duration lockoutDuration;
	private final ConcurrentHashMap<String, Attempt> attemptsByEmail = new ConcurrentHashMap<>();

	public LoginAttemptService(@Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
			@Value("${app.auth.lockout-duration-minutes:15}") long lockoutDurationMinutes) {
		this.maxFailedAttempts = maxFailedAttempts;
		this.lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes);
	}

	public boolean isLocked(String email) {
		Attempt attempt = attemptsByEmail.get(key(email));
		return attempt != null && attempt.lockedUntil != null && attempt.lockedUntil.isAfter(Instant.now());
	}

	public void recordFailure(String email) {
		Attempt attempt = attemptsByEmail.computeIfAbsent(key(email), k -> new Attempt());
		if (attempt.count.incrementAndGet() >= maxFailedAttempts) {
			attempt.lockedUntil = Instant.now().plus(lockoutDuration);
			attempt.count.set(0);
		}
	}

	public void recordSuccess(String email) {
		attemptsByEmail.remove(key(email));
	}

	private String key(String email) {
		return email == null ? "" : email.toLowerCase();
	}

	private static final class Attempt {
		private final AtomicInteger count = new AtomicInteger(0);
		private volatile Instant lockedUntil;
	}
}
