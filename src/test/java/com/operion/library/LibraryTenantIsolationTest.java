package com.operion.library;

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
 * ExaminationTenantIsolationTest / CommunicationTenantIsolationTest /
 * TransportTenantIsolationTest) to the Library tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LibraryTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private BookRepository bookRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsBooks() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "library-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		bookRepository.save(new Book("978-1-1", "Org A Book", "Author A", null, null, null));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "library-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		bookRepository.save(new Book("978-2-2", "Org B Book", "Author B", null, null, null));

		TenantContext.set(orgA.getId(), null);
		List<Book> visibleToA = bookRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
		assertThat(visibleToA.get(0).getTitle()).isEqualTo("Org A Book");
	}
}
