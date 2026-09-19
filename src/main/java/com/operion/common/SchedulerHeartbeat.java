package com.operion.common;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Liveness signal for the DB-backed @Scheduled pollers (NotificationDispatchWorker and
 * friends) - there's no job-queue table to ping (see SystemHealthController's javadoc),
 * so "is the background-job scheduler alive" is approximated as "did a poller tick
 * recently". Starts healthy at construction so a freshly-booted app doesn't report
 * "down" before the first tick lands.
 */
@Component
public class SchedulerHeartbeat {

	private static final long STALE_AFTER_MILLIS = 5 * 60_000;

	private final AtomicReference<Instant> lastTick = new AtomicReference<>(Instant.now());

	public void tick() {
		lastTick.set(Instant.now());
	}

	public boolean isHealthy() {
		return lastTick.get().isAfter(Instant.now().minusMillis(STALE_AFTER_MILLIS));
	}
}
