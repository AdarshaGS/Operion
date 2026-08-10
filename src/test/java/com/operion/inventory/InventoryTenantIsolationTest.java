package com.operion.inventory;

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
 * TransportTenantIsolationTest / LibraryTenantIsolationTest) to the Inventory tables.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InventoryTenantIsolationTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void queriesOnlySeeTheCurrentTenantsItemCategories() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "inventory-iso-a-school"));
		TenantContext.set(orgA.getId(), null);
		itemCategoryRepository.save(new ItemCategory("STATIONERY", "Org A Stationery", null));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "inventory-iso-b-school"));
		TenantContext.set(orgB.getId(), null);
		itemCategoryRepository.save(new ItemCategory("STATIONERY", "Org B Stationery", null));

		TenantContext.set(orgA.getId(), null);
		List<ItemCategory> visibleToA = itemCategoryRepository.findAll();

		assertThat(visibleToA).hasSize(1);
		assertThat(visibleToA.get(0).getOrganisationId()).isEqualTo(orgA.getId());
		assertThat(visibleToA.get(0).getName()).isEqualTo("Org A Stationery");
	}
}
