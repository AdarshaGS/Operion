package com.operion.communication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.calendar.WorkingDayService;
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
 * A due announcement whose day is a holiday or weekly off (for its campus) is left as a
 * DRAFT and goes out on the next tick that lands on a working day, unless it was created
 * with sendOnNonWorkingDays.
 */
@Component
public class ScheduledAnnouncementPublisher {

	private static final long POLL_INTERVAL_MILLIS = 60_000;

	private final OrganisationRepository organisationRepository;
	private final AnnouncementRepository announcementRepository;
	private final CommunicationService communicationService;
	private final WorkingDayService workingDayService;

	public ScheduledAnnouncementPublisher(OrganisationRepository organisationRepository, AnnouncementRepository announcementRepository,
			CommunicationService communicationService, WorkingDayService workingDayService) {
		this.organisationRepository = organisationRepository;
		this.announcementRepository = announcementRepository;
		this.communicationService = communicationService;
		this.workingDayService = workingDayService;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void publishDueAnnouncements() {
		Instant now = Instant.now();
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				List<Announcement> due = announcementRepository.findByStatusAndScheduledAtLessThanEqual(AnnouncementStatus.DRAFT, now);
				if (due.isEmpty()) {
					continue;
				}
				LocalDate today = workingDayService.today();
				for (Announcement announcement : due) {
					Long campusId = announcement.getCampus() != null ? announcement.getCampus().getId() : null;
					if (announcement.isSendOnNonWorkingDays() || workingDayService.isWorkingDay(today, campusId)) {
						communicationService.publishAnnouncement(announcement);
					}
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
