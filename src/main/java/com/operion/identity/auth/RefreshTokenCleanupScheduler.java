package com.operion.identity.auth;

import java.time.Instant;

import com.operion.common.SchedulerHeartbeat;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes refresh tokens that can no longer be used - revoked (every refresh rotates, so each login
 * leaves a trail of them) or expired - so the table, which consumeAndRevoke scans, does not grow forever.
 * Refresh tokens are tenant-scoped, so, like OverdueInvoiceReminderScheduler, this loops organisations
 * and sets TenantContext for each: the delete only ever touches that organisation's rows.
 */
@Component
public class RefreshTokenCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);
	private static final long POLL_INTERVAL_MILLIS = 24 * 60 * 60_000;

	private final OrganisationRepository organisationRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final SchedulerHeartbeat schedulerHeartbeat;

	public RefreshTokenCleanupScheduler(OrganisationRepository organisationRepository, RefreshTokenRepository refreshTokenRepository,
			SchedulerHeartbeat schedulerHeartbeat) {
		this.organisationRepository = organisationRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.schedulerHeartbeat = schedulerHeartbeat;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void deleteSpentTokens() {
		schedulerHeartbeat.tick();
		Instant now = Instant.now();
		int deleted = 0;
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				deleted += refreshTokenRepository.deleteSpent(now);
			} finally {
				TenantContext.clear();
			}
		}
		if (deleted > 0) {
			log.info("Refresh token cleanup removed {} spent token(s)", deleted);
		}
	}
}
