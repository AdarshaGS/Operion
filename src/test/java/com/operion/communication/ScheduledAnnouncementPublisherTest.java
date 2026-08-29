package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
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
 * TenantContext rather than only the first (see ScheduledAnnouncementPublisher's class doc).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, CommunicationService.class, ScheduledAnnouncementPublisher.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduledAnnouncementPublisherTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AnnouncementRepository announcementRepository;

	@Autowired
	private ScheduledAnnouncementPublisher scheduledAnnouncementPublisher;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void publishesOnlyDueDraftsAcrossEveryOrganisation() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "sched-pub-a"));
		TenantContext.set(orgA.getId(), null);
		Announcement dueInOrgA = announcementRepository.save(
				new Announcement(null, "Due Notice", "Body", AudienceType.ORG, null, Instant.now().minusSeconds(60)));
		Announcement notYetDueInOrgA = announcementRepository.save(
				new Announcement(null, "Future Notice", "Body", AudienceType.ORG, null, Instant.now().plusSeconds(3600)));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "sched-pub-b"));
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
}
