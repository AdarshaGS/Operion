package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
 * Extends the standing tenant-isolation proof (OrganisationTenantIsolationTest /
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest / FeeTenantIsolationTest /
 * ExaminationTenantIsolationTest) to the Communication tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommunicationTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private AnnouncementRepository announcementRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsAnnouncements() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "comm-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		announcementRepository.save(new Announcement(null, "Org A Notice", "Body", AudienceType.ORG, null));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "comm-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		announcementRepository.save(new Announcement(null, "Org B Notice", "Body", AudienceType.ORG, null));

		TenantContext.set(orgA.getId(), null);
		List<Announcement> visibleToA = announcementRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
	}
}
