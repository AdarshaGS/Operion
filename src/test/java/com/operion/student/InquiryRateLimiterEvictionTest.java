package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InquiryRateLimiterEvictionTest {

	@Test
	void refusesAfterTheThresholdForOneKey() {
		InquiryRateLimiter limiter = new InquiryRateLimiter();

		for (int i = 0; i < InquiryRateLimiter.MAX_PER_WINDOW; i++) {
			assertThat(limiter.tryAcquire("1.2.3.4|school", 1_000 + i)).isTrue();
		}
		assertThat(limiter.tryAcquire("1.2.3.4|school", 2_000)).isFalse();
	}

	@Test
	void theKeyMapIsBoundedWhenCallersSprayUniqueKeys() {
		InquiryRateLimiter limiter = new InquiryRateLimiter();

		for (int i = 0; i < InquiryRateLimiter.MAX_KEYS + 5_000; i++) {
			limiter.tryAcquire("addr-" + i + "|school", 1_000 + i);
		}

		assertThat(limiter.trackedKeys()).isEqualTo(InquiryRateLimiter.MAX_KEYS);
	}
}
