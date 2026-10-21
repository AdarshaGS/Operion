package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.operion.calendar.WorkingDayService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfiguration;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the poller only auto-publishes a DRAFT whose scheduledAt has arrived, leaves a
 * not-yet-due DRAFT alone, and correctly iterates every organisation under its own
 * TenantContext rather than only the first (see ScheduledAnnouncementPublisher's class doc),
 * and that a due announcement is held back on a holiday / weekly off unless it opted out.
 * Each organisation gets an explicit weekly-off mask so the outcome never depends on the
 * weekday the test happens to run on.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, CommunicationService.class, ScheduledAnnouncementPublisher.class,
		WorkingDayService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduledAnnouncementPublisherTest {

	private static final int EVERY_DAY = 0b1111111;
	private static final String ZONE = "UTC";

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AnnouncementRepository announcementRepository;

	@Autowired
	private OrganisationConfigurationRepository configurationRepository;

	@Autowired
	private ScheduledAnnouncementPublisher scheduledAnnouncementPublisher;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void publishesOnlyDueDraftsAcrossEveryOrganisation() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "sched-pub-a"));
		setWorkingDaysMask(orgA, EVERY_DAY);
		TenantContext.set(orgA.getId(), null);
		Announcement dueInOrgA = announcementRepository.save(
				new Announcement(null, "Due Notice", "Body", AudienceType.ORG, null, Instant.now().minusSeconds(60)));
		Announcement notYetDueInOrgA = announcementRepository.save(
				new Announcement(null, "Future Notice", "Body", AudienceType.ORG, null, Instant.now().plusSeconds(3600)));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "sched-pub-b"));
		setWorkingDaysMask(orgB, EVERY_DAY);
		TenantContext.set(orgB.getId(), null);
		Announcement dueInOrgB = announcementRepository.save(
				new Announcement(null, "Due Notice B", "Body", AudienceType.ORG, null, Instant.now().minusSeconds(60)));

		TenantContext.clear();
		scheduledAnnouncementPublisher.publishDueAnnouncements();

		TenantContext.set(orgA.getId(), null);
		assertThat(announcementRepository.findById(dueInOrgA.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(announcementRepository.findById(notYetDueInOrgA.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.DRAFT);

		TenantContext.set(orgB.getId(), null);
		assertThat(announcementRepository.findById(dueInOrgB.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
	}

	private void setWorkingDaysMask(Organisation organisation, int mask) {
		OrganisationConfiguration configuration = configurationRepository.findById(organisation.getId())
				.orElseGet(() -> new OrganisationConfiguration(organisation.getId(), ZONE));
		configuration.setWorkingDaysMask(mask);
		configurationRepository.save(configuration);
	}

	@Test
	void holdsADueAnnouncementOnAWeeklyOffDayUnlessItOptedOutThenSendsItOnceTodayIsWorking() {
		int todayBit = 1 << LocalDate.now(ZoneId.of(ZONE)).getDayOfWeek().ordinal();
		Organisation org = organisationRepository.save(new Organisation("C School", "C School Trust", "sched-pub-offday"));
		setWorkingDaysMask(org, EVERY_DAY & ~todayBit); // today is the weekly off
		TenantContext.set(org.getId(), null);
		Announcement held = announcementRepository.save(
				new Announcement(null, "Routine", "Body", AudienceType.ORG, null, Instant.now().minusSeconds(60)));
		Announcement urgent = new Announcement(null, "School closed tomorrow", "Body", AudienceType.ORG, null, Instant.now().minusSeconds(60));
		urgent.allowOnNonWorkingDays();
		urgent = announcementRepository.save(urgent);

		TenantContext.clear();
		scheduledAnnouncementPublisher.publishDueAnnouncements();

		TenantContext.set(org.getId(), null);
		assertThat(announcementRepository.findById(held.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.DRAFT);
		assertThat(announcementRepository.findById(urgent.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
		TenantContext.clear();

		setWorkingDaysMask(org, EVERY_DAY); // the next working day arrives
		scheduledAnnouncementPublisher.publishDueAnnouncements();

		TenantContext.set(org.getId(), null);
		assertThat(announcementRepository.findById(held.getId()).orElseThrow().getStatus()).isEqualTo(AnnouncementStatus.PUBLISHED);
	}
}
