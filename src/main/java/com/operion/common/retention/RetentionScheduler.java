package com.operion.common.retention;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.operion.common.SchedulerHeartbeat;
import com.operion.common.TenantContext;
import com.operion.communication.DeliveryStatus;
import com.operion.communication.NotificationRecipientRepository;
import com.operion.email.EmailDeliveryStatus;
import com.operion.email.EmailOutboxRepository;
import com.operion.identity.auth.LoginAttemptRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly purge of rows that only ever grow and carry personal data: login_attempts, email_outbox and
 * notification_recipients (which holds each message body). audit_logs is NOT touched. Like
 * RefreshTokenCleanupScheduler it loops organisations and sets TenantContext for each, so a delete only
 * ever reaches that organisation's rows. Rows go a page of ids at a time (select ids with a limit, then
 * delete by id) rather than {@code DELETE ... LIMIT}, which H2 does not support; this keeps each statement
 * and transaction small on a large backlog.
 */
@Component
public class RetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

	private final OrganisationRepository organisationRepository;
	private final LoginAttemptRepository loginAttemptRepository;
	private final EmailOutboxRepository emailOutboxRepository;
	private final NotificationRecipientRepository notificationRecipientRepository;
	private final RetentionProperties properties;
	private final SchedulerHeartbeat schedulerHeartbeat;

	public RetentionScheduler(OrganisationRepository organisationRepository, LoginAttemptRepository loginAttemptRepository,
			EmailOutboxRepository emailOutboxRepository, NotificationRecipientRepository notificationRecipientRepository,
			RetentionProperties properties, SchedulerHeartbeat schedulerHeartbeat) {
		this.organisationRepository = organisationRepository;
		this.loginAttemptRepository = loginAttemptRepository;
		this.emailOutboxRepository = emailOutboxRepository;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.properties = properties;
		this.schedulerHeartbeat = schedulerHeartbeat;
	}

	@Scheduled(cron = "${app.retention.cron:0 30 3 * * *}", zone = "${app.retention.zone:}")
	public void scheduledPurge() {
		if (!properties.isEnabled()) {
			return;
		}
		schedulerHeartbeat.tick();
		purgeExpired(Instant.now());
	}

	/** Runs one purge pass as of {@code now}; public so it can be driven directly (tests, an ops trigger). */
	public void purgeExpired(Instant now) {
		for (Organisation organisation : organisationRepository.findAll()) {
			Long organisationId = organisation.getId();
			TenantContext.set(organisationId, null);
			try {
				int loginAttempts = purge(loginAttemptRepository,
						page -> loginAttemptRepository.findPurgeableKeys(organisationId, now.minus(Duration.ofDays(properties.getLoginAttemptsDays())), now, page));
				int emails = purge(emailOutboxRepository,
						page -> emailOutboxRepository.findIdsByStatusInAndCreatedAtBefore(
								List.of(EmailDeliveryStatus.SENT, EmailDeliveryStatus.FAILED),
								now.minus(Duration.ofDays(properties.getEmailOutboxDays())), page));
				int notifications = purge(notificationRecipientRepository,
						page -> notificationRecipientRepository.findIdsByDeliveryStatusInAndCreatedAtBefore(
								List.of(DeliveryStatus.DELIVERED, DeliveryStatus.READ, DeliveryStatus.FAILED),
								now.minus(Duration.ofDays(properties.getNotificationRecipientsDays())), page));
				if (loginAttempts + emails + notifications > 0) {
					log.info("Retention purge for organisation {} removed {} login attempt(s), {} email outbox row(s), {} notification recipient(s)",
							organisationId, loginAttempts, emails, notifications);
				}
			} finally {
				TenantContext.clear();
			}
		}
	}

	/** Deletes page after page until a page comes back empty; returns how many rows went. */
	private <T, ID> int purge(JpaRepository<T, ID> repository, Function<Pageable, List<ID>> selectPage) {
		Pageable page = PageRequest.of(0, Math.max(1, properties.getBatchSize()));
		int deleted = 0;
		List<ID> ids;
		while (!(ids = selectPage.apply(page)).isEmpty()) {
			repository.deleteAllByIdInBatch(ids);
			deleted += ids.size();
		}
		return deleted;
	}
}
