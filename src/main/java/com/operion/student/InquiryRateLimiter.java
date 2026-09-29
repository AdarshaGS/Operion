package com.operion.student;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Basic in-memory sliding-window limiter for the public inquiry form (per IP + org slug). Single-node only. */
@Component
public class InquiryRateLimiter {

	static final int MAX_PER_WINDOW = 10;
	static final Duration WINDOW = Duration.ofHours(1);

	private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	/** Records an attempt; returns false when the caller is over the limit. */
	public boolean tryAcquire(String key) {
		return tryAcquire(key, System.currentTimeMillis());
	}

	boolean tryAcquire(String key, long nowMillis) {
		Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (deque) {
			long cutoff = nowMillis - WINDOW.toMillis();
			while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
				deque.pollFirst();
			}
			if (deque.size() >= MAX_PER_WINDOW) {
				return false;
			}
			deque.addLast(nowMillis);
			return true;
		}
	}
}
