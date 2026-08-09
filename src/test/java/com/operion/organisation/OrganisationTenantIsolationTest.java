package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tenant-isolation spike called for in ai-context/erp-system-plan.md §1.4: proves
 * Hibernate's @TenantId (see TenantScopedEntity) both auto-stamps the current tenant
 * on insert and auto-filters every query by it, with no per-repository-method code.
 *
 * Propagation.NOT_SUPPORTED overrides @DataJpaTest's default of wrapping the whole test
 * in one rolled-back transaction: Hibernate resolves the tenant identifier once, when a
 * session opens, so each repository call below needs its own fresh transaction/session
 * for a TenantContext switch to actually take effect - exactly like separate HTTP
 * requests would in production.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsRows() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "a-school"));
		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "b-school"));

		TenantContext.set(orgA.getId(), null);
		campusRepository.save(new Campus("A Main Campus", "MAIN"));

		TenantContext.set(orgB.getId(), null);
		campusRepository.save(new Campus("B Main Campus", "MAIN"));

		TenantContext.set(orgA.getId(), null);
		List<Campus> visibleToA = campusRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getName()).isEqualTo("A Main Campus");
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
	}
}
