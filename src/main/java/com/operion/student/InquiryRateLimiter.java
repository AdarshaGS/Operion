package com.operion.student;

import java.time.Duration;

import com.operion.common.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

/** Per-caller limiter for the public inquiry form (per client address + org slug); a thin wrapper over the shared,
 * bounded {@link SlidingWindowRateLimiter}. Single-node only. */
@Component
public class InquiryRateLimiter {

	static final int MAX_PER_WINDOW = 10;
	static final Duration WINDOW = Duration.ofHours(1);
	static final int MAX_KEYS = 10_000;

	private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(MAX_PER_WINDOW, WINDOW, MAX_KEYS);

	/** Records an attempt; returns false when the caller is over the limit. */
	public boolean tryAcquire(String key) {
		return limiter.tryAcquire(key);
	}

	boolean tryAcquire(String key, long nowMillis) {
		return limiter.tryAcquire(key, nowMillis);
	}

	int trackedKeys() {
		return limiter.trackedKeys();
	}
}
