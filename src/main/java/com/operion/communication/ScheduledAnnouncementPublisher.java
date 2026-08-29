package com.operion.communication;

import java.time.Instant;

import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Backs Announcement.scheduledAt: every tick, for every organisation, publishes any DRAFT
 * announcement whose scheduledAt has arrived - a DB-backed poller rather than a queue, per
 * ai-context/erp-system-plan.md §3.3's "no Kafka" stance for Communication's async work.
 * Runs once per tenant under that tenant's TenantContext, since Announcement (like every
 * TenantScopedEntity) auto-filters queries by whatever organisation is currently set.
 */
@Component
public class ScheduledAnnouncementPublisher {

	private static final long POLL_INTERVAL_MILLIS = 60_000;

	private final OrganisationRepository organisationRepository;
	private final AnnouncementRepository announcementRepository;
	private final CommunicationService communicationService;

	public ScheduledAnnouncementPublisher(OrganisationRepository organisationRepository, AnnouncementRepository announcementRepository,
			CommunicationService communicationService) {
		this.organisationRepository = organisationRepository;
		this.announcementRepository = announcementRepository;
		this.communicationService = communicationService;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void publishDueAnnouncements() {
		Instant now = Instant.now();
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				announcementRepository.findByStatusAndScheduledAtLessThanEqual(AnnouncementStatus.DRAFT, now)
						.forEach(communicationService::publishAnnouncement);
			} finally {
				TenantContext.clear();
			}
		}
	}
}
