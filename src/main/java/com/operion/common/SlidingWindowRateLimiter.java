package com.operion.common;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small in-memory sliding-window limiter for public (unauthenticated) endpoints: at most {@code maxPerWindow}
 * accepted attempts per key inside {@code window}. Single-node only - there is no shared store, so behind
 * several instances each enforces its own count.
 *
 * <p>Memory is bounded: expired keys are dropped on every call, and once {@code maxKeys} distinct keys are
 * tracked the least-recently-used one is evicted, so a caller spraying unique keys cannot grow the map without
 * limit. (The trade-off is that sustained key spraying can push a genuine caller's counter out early.)
 * Only accepted attempts count towards the window; a rejected attempt does not extend the lockout.
 */
public final class SlidingWindowRateLimiter {

	private final int maxPerWindow;
	private final long windowMillis;
	private final int maxKeys;
	/** Access-ordered so the eldest entry is always the least recently used one. */
	private final LinkedHashMap<String, ArrayDeque<Long>> hits = new LinkedHashMap<>(16, 0.75f, true);

	public SlidingWindowRateLimiter(int maxPerWindow, Duration window, int maxKeys) {
		if (maxPerWindow < 1 || maxKeys < 1 || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("maxPerWindow, maxKeys and window must all be positive");
		}
		this.maxPerWindow = maxPerWindow;
		this.windowMillis = window.toMillis();
		this.maxKeys = maxKeys;
	}

	/** Records an attempt; returns false when the key is over the limit. */
	public boolean tryAcquire(String key) {
		return tryAcquire(key, System.currentTimeMillis());
	}

	public synchronized boolean tryAcquire(String key, long nowMillis) {
		long cutoff = nowMillis - windowMillis;
		evictExpired(cutoff);

		ArrayDeque<Long> deque = hits.get(key);
		if (deque == null) {
			while (hits.size() >= maxKeys) {
				Iterator<Map.Entry<String, ArrayDeque<Long>>> eldest = hits.entrySet().iterator();
				eldest.next();
				eldest.remove();
			}
			deque = new ArrayDeque<>();
			hits.put(key, deque);
		}
		while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
			deque.pollFirst();
		}
		if (deque.size() >= maxPerWindow) {
			return false;
		}
		deque.addLast(nowMillis);
		return true;
	}

	/** True when the key has used up its allowance in the window. Records nothing - pair with {@link #tryAcquire} on failure. */
	public boolean isBlocked(String key) {
		return isBlocked(key, System.currentTimeMillis());
	}

	public synchronized boolean isBlocked(String key, long nowMillis) {
		ArrayDeque<Long> deque = hits.get(key);
		if (deque == null) {
			return false;
		}
		long cutoff = nowMillis - windowMillis;
		int live = 0;
		for (long hit : deque) {
			if (hit >= cutoff) {
				live++;
			}
		}
		return live >= maxPerWindow;
	}

	/** Number of keys currently tracked - exposed so the eviction bound can be asserted. */
	public synchronized int trackedKeys() {
		return hits.size();
	}

	/** Drops keys from the least-recently-used end whose newest hit has already left the window. */
	private void evictExpired(long cutoff) {
		Iterator<ArrayDeque<Long>> iterator = hits.values().iterator();
		while (iterator.hasNext()) {
			ArrayDeque<Long> deque = iterator.next();
			if (deque.isEmpty() || deque.peekLast() < cutoff) {
				iterator.remove();
			} else {
				break;
			}
		}
	}
}
