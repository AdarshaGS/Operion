package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
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
 * Proves InventoryService.getLowStockItems only returns active items with a reorder
 * level set whose computed balance is at or below it, scoped per campus - mirrors
 * StockBalanceTest's fixture shape.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LowStockTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private InventoryService inventoryService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(ItemCategory category, Campus campus, Campus otherCampus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus otherCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));

		return new Fixture(category, campus, otherCampus);
	}

	@Test
	void itemWithoutReorderLevelIsNeverLowStock() {
		Fixture fixture = setUpFixture("low-stock-no-threshold");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		assertThat(inventoryService.getLowStockItems(fixture.campus())).isEmpty();
		assertThat(item.getReorderLevel()).isNull();
	}

	@Test
	void itemAboveReorderLevelIsNotFlagged() {
		Fixture fixture = setUpFixture("low-stock-above-threshold");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, 10);
		inventoryService.recordEntry(item, fixture.campus(), 20, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		assertThat(inventoryService.getLowStockItems(fixture.campus())).isEmpty();
	}

	@Test
	void itemAtOrBelowReorderLevelIsFlaggedWithBalance() {
		Fixture fixture = setUpFixture("low-stock-at-threshold");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, 10);
		inventoryService.recordEntry(item, fixture.campus(), 15, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordIssue(item, fixture.campus(), 6, LocalDate.of(2026, 1, 10), "Class 5B", "Classwork", null);

		assertThat(inventoryService.getLowStockItems(fixture.campus()))
				.singleElement()
				.satisfies(lowStockItem -> {
					assertThat(lowStockItem.item().getId()).isEqualTo(item.getId());
					assertThat(lowStockItem.balance()).isEqualTo(9);
				});
	}

	@Test
	void lowStockIsScopedPerCampus() {
		Fixture fixture = setUpFixture("low-stock-per-campus");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, 10);
		inventoryService.recordEntry(item, fixture.campus(), 2, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordEntry(item, fixture.otherCampus(), 50, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		assertThat(inventoryService.getLowStockItems(fixture.campus())).hasSize(1);
		assertThat(inventoryService.getLowStockItems(fixture.otherCampus())).isEmpty();
	}
}
