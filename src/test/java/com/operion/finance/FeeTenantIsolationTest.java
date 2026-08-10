package com.operion.finance;

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
 * StudentTenantIsolationTest / AttendanceTenantIsolationTest) to the Fee tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeeTenantIsolationTest {

	@Autowired
	private FeeService feeService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private FeeCategoryRepository feeCategoryRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsFeeCategories() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "fee-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		feeService.createCategory("TUITION", "Tuition Fee", null);

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "fee-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		feeService.createCategory("TUITION", "Tuition Fee", null);

		TenantContext.set(orgA.getId(), null);
		List<FeeCategory> visibleToA = feeCategoryRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
	}
}
