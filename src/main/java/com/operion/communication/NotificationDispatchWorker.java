package com.operion.communication;

import com.operion.common.SchedulerHeartbeat;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Backs real EMAIL/SMS delivery: every tick, for every organisation, dispatches any
 * PENDING NotificationRecipient row via NotificationDispatchService - a DB-backed poller
 * rather than a queue, same convention as ScheduledAnnouncementPublisher (see its class
 * doc for the "no Kafka" reasoning and the per-tenant TenantContext looping it shares).
 * Also feeds SchedulerHeartbeat - the platform dashboard's only signal that background
 * jobs are alive at all (see SystemHealthController).
 */
@Component
public class NotificationDispatchWorker {

	private static final long POLL_INTERVAL_MILLIS = 30_000;

	private final OrganisationRepository organisationRepository;
	private final NotificationRecipientRepository notificationRecipientRepository;
	private final NotificationDispatchService notificationDispatchService;
	private final SchedulerHeartbeat schedulerHeartbeat;

	public NotificationDispatchWorker(OrganisationRepository organisationRepository,
			NotificationRecipientRepository notificationRecipientRepository, NotificationDispatchService notificationDispatchService,
			SchedulerHeartbeat schedulerHeartbeat) {
		this.organisationRepository = organisationRepository;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.notificationDispatchService = notificationDispatchService;
		this.schedulerHeartbeat = schedulerHeartbeat;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void dispatchPendingNotifications() {
		schedulerHeartbeat.tick();
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				notificationRecipientRepository.findByDeliveryStatus(DeliveryStatus.PENDING)
						.forEach(notificationDispatchService::dispatch);
			} finally {
				TenantContext.clear();
			}
		}
	}
}
