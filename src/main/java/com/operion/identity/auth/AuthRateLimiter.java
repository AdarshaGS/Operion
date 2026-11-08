package com.operion.identity.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import com.operion.common.ClientIpResolver;
import com.operion.common.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window throttles for the public/credential-guessing auth endpoints, keyed by
 * client IP (and, for password-reset requests, by account email). It complements - does not replace -
 * the database-backed per-account lockout in LoginAttemptService: that one stops guessing against one
 * account from anywhere, this one stops one source from spraying many accounts.
 *
 * Deliberately per instance and non-persistent (a restart or a second node starts a fresh window):
 * the per-account lockout is the shared, durable guard, and this one exists to blunt single-source
 * floods cheaply. If the deployment runs several nodes, the effective limit is limit x nodes.
 *
 * The client IP comes from the shared ClientIpResolver: request.getRemoteAddr() unless
 * app.security.trusted-proxy-header names a header set by a proxy we trust (off by default, since a
 * client-supplied X-Forwarded-For is trivially forged).
 *
 * Login and change-password count FAILURES only, with generous limits, because a whole school can
 * share one NAT address and sign in together each morning.
 */
@Component
public class AuthRateLimiter {

	private static final int PRUNE_THRESHOLD = 10_000;

	private final Clock clock;
	private final ClientIpResolver clientIpResolver;
	private final int loginFailuresPerIp;
	private final int passwordChangeFailuresPerIp;
	private final Duration failureWindow;
	private final int resetRequestsPerEmail;
	private final int resetRequestsPerIp;
	private final Duration resetWindow;
	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	@Autowired
	public AuthRateLimiter(ClientIpResolver clientIpResolver,
			@Value("${app.auth.ip-login-max-failures:50}") int loginFailuresPerIp,
			@Value("${app.auth.ip-password-change-max-failures:20}") int passwordChangeFailuresPerIp,
			@Value("${app.auth.ip-failure-window-minutes:15}") long failureWindowMinutes,
			@Value("${app.auth.reset-max-requests-per-email:3}") int resetRequestsPerEmail,
			@Value("${app.auth.reset-max-requests-per-ip:20}") int resetRequestsPerIp,
			@Value("${app.auth.reset-window-minutes:60}") long resetWindowMinutes) {
		this(Clock.systemUTC(), clientIpResolver, loginFailuresPerIp, passwordChangeFailuresPerIp, Duration.ofMinutes(failureWindowMinutes),
				resetRequestsPerEmail, resetRequestsPerIp, Duration.ofMinutes(resetWindowMinutes));
	}

	AuthRateLimiter(Clock clock, ClientIpResolver clientIpResolver, int loginFailuresPerIp, int passwordChangeFailuresPerIp,
			Duration failureWindow, int resetRequestsPerEmail, int resetRequestsPerIp, Duration resetWindow) {
		this.clock = clock;
		this.clientIpResolver = clientIpResolver;
		this.loginFailuresPerIp = loginFailuresPerIp;
		this.passwordChangeFailuresPerIp = passwordChangeFailuresPerIp;
		this.failureWindow = failureWindow;
		this.resetRequestsPerEmail = resetRequestsPerEmail;
		this.resetRequestsPerIp = resetRequestsPerIp;
		this.resetWindow = resetWindow;
	}

	public String clientIp(HttpServletRequest request) {
		return clientIpResolver.resolve(request);
	}

	/** Throws 429 when this IP has already failed too many logins in the window. */
	public void checkLoginAllowed(String ip) {
		if (isFull("login-fail:" + ip, loginFailuresPerIp, failureWindow)) {
			throw new TooManyRequestsException("Too many failed sign-in attempts. Try again later.");
		}
	}

	public void recordLoginFailure(String ip) {
		record("login-fail:" + ip, failureWindow);
	}

	public void checkPasswordChangeAllowed(String ip) {
		if (isFull("pwchange-fail:" + ip, passwordChangeFailuresPerIp, failureWindow)) {
			throw new TooManyRequestsException("Too many incorrect password attempts. Try again later.");
		}
	}

	public void recordPasswordChangeFailure(String ip) {
		record("pwchange-fail:" + ip, failureWindow);
	}

	/**
	 * Counts a password-reset request against both the email and the IP and says whether it may
	 * proceed. A false return is NOT surfaced to the caller as an error: the endpoint answers the same
	 * generic acknowledgement either way, so throttling cannot be used to tell whether an account exists.
	 * The email key is org-scoped and accent/case-normalised like the lockout key.
	 */
	public boolean allowResetRequest(String ip, String organisationSlug, String email) {
		String emailKey = "reset-email:" + organisationSlug + ":" + LoginAttemptService.normaliseEmail(email);
		boolean emailOk = tryAcquire(emailKey, resetRequestsPerEmail, resetWindow);
		boolean ipOk = tryAcquire("reset-ip:" + ip, resetRequestsPerIp, resetWindow);
		return emailOk && ipOk;
	}

	private boolean isFull(String key, int limit, Duration window) {
		Bucket bucket = buckets.get(key);
		if (bucket == null) {
			return false;
		}
		synchronized (bucket) {
			bucket.evictOlderThan(clock.millis() - window.toMillis());
			return bucket.hits.size() >= limit;
		}
	}

	private void record(String key, Duration window) {
		tryAcquire(key, Integer.MAX_VALUE, window);
	}

	private boolean tryAcquire(String key, int limit, Duration window) {
		pruneIfLarge();
		long now = clock.millis();
		Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(window.toMillis()));
		synchronized (bucket) {
			bucket.evictOlderThan(now - window.toMillis());
			if (bucket.hits.size() >= limit) {
				return false;
			}
			bucket.hits.addLast(now);
			return true;
		}
	}

	/** Bounds memory against a flood of distinct keys: drops every bucket whose newest hit has aged out. */
	private void pruneIfLarge() {
		if (buckets.size() < PRUNE_THRESHOLD) {
			return;
		}
		long now = clock.millis();
		buckets.entrySet().removeIf(entry -> {
			Bucket bucket = entry.getValue();
			synchronized (bucket) {
				Long newest = bucket.hits.peekLast();
				return newest == null || newest <= now - bucket.windowMillis;
			}
		});
	}

	private static final class Bucket {

		private final Deque<Long> hits = new ArrayDeque<>();
		private final long windowMillis;

		private Bucket(long windowMillis) {
			this.windowMillis = windowMillis;
		}

		private void evictOlderThan(long cutoff) {
			while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
				hits.pollFirst();
			}
		}
	}
}
