package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

	@Test
	void allowsUpToTheThresholdThenRefuses() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, Duration.ofMinutes(1), 100);

		assertThat(limiter.tryAcquire("a", 1_000)).isTrue();
		assertThat(limiter.tryAcquire("a", 1_001)).isTrue();
		assertThat(limiter.tryAcquire("a", 1_002)).isTrue();
		assertThat(limiter.tryAcquire("a", 1_003)).isFalse();
	}

	@Test
	void keysAreCountedIndependently() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMinutes(1), 100);

		assertThat(limiter.tryAcquire("a", 1_000)).isTrue();
		assertThat(limiter.tryAcquire("a", 1_001)).isFalse();
		assertThat(limiter.tryAcquire("b", 1_002)).isTrue();
	}

	@Test
	void theWindowSlidesSoAnOldAttemptStopsCounting() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(2, Duration.ofMillis(1_000), 100);

		assertThat(limiter.tryAcquire("a", 0)).isTrue();
		assertThat(limiter.tryAcquire("a", 500)).isTrue();
		assertThat(limiter.tryAcquire("a", 900)).isFalse();
		// The first hit (t=0) has left the window by t=1_100, the second (t=500) has not.
		assertThat(limiter.tryAcquire("a", 1_100)).isTrue();
		assertThat(limiter.tryAcquire("a", 1_200)).isFalse();
	}

	@Test
	void aRefusedAttemptDoesNotExtendTheLockout() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMillis(1_000), 100);

		assertThat(limiter.tryAcquire("a", 0)).isTrue();
		assertThat(limiter.tryAcquire("a", 900)).isFalse();
		assertThat(limiter.tryAcquire("a", 1_100)).isTrue();
	}

	@Test
	void trackedKeysNeverExceedTheBoundWhenKeysAreSprayed() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofHours(1), 50);

		for (int i = 0; i < 10_000; i++) {
			limiter.tryAcquire("spray-" + i, 1_000 + i);
			assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(50);
		}
		assertThat(limiter.trackedKeys()).isEqualTo(50);
	}

	@Test
	void theLeastRecentlyUsedKeyIsTheOneEvicted() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofHours(1), 2);

		limiter.tryAcquire("old", 1);
		limiter.tryAcquire("busy", 2);
		limiter.tryAcquire("busy", 3); // refused, but touches "busy" so "old" is now the eldest
		limiter.tryAcquire("new", 4); // evicts "old"

		assertThat(limiter.trackedKeys()).isEqualTo(2);
		assertThat(limiter.tryAcquire("busy", 5)).isFalse(); // still tracked and still limited
		assertThat(limiter.tryAcquire("old", 6)).isTrue(); // forgotten, so it starts fresh
	}

	@Test
	void expiredKeysAreDroppedSoTheMapShrinksBackDown() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofMillis(1_000), 1_000);

		for (int i = 0; i < 100; i++) {
			limiter.tryAcquire("k" + i, 0);
		}
		assertThat(limiter.trackedKeys()).isEqualTo(100);

		limiter.tryAcquire("later", 10_000);

		assertThat(limiter.trackedKeys()).isEqualTo(1);
	}

	@Test
	void rejectsNonsensicalConfiguration() {
		assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, Duration.ofMinutes(1), 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SlidingWindowRateLimiter(1, Duration.ZERO, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SlidingWindowRateLimiter(1, Duration.ofMinutes(1), 0)).isInstanceOf(IllegalArgumentException.class);
	}
}
