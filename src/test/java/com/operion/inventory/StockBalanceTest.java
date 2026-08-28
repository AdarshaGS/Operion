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
 * Proves InventoryService.getBalance computes correctly across a mix of entries,
 * issues, and adjustments - entries increase it, issues decrease it, adjustment deltas
 * apply signed, and balance is scoped per item+campus (a second campus's ledger
 * doesn't leak into the first's balance).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockBalanceTest {

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

	private record Fixture(Item item, Campus campus, Campus otherCampus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus otherCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		return new Fixture(item, campus, otherCampus);
	}

	@Test
	void balanceStartsAtZeroWithNoLedgerActivity() {
		Fixture fixture = setUpFixture("inventory-balance-zero");
		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isZero();
	}

	@Test
	void entriesIncreaseBalance() {
		Fixture fixture = setUpFixture("inventory-balance-entries");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 100, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 50, null, LocalDate.of(2026, 1, 5), "Vendor B", null);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(150);
	}

	@Test
	void issuesDecreaseBalance() {
		Fixture fixture = setUpFixture("inventory-balance-issues");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 100, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordIssue(fixture.item(), fixture.campus(), 30, LocalDate.of(2026, 1, 10), "Class 5B", "Classwork", null);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(70);
	}

	@Test
	void adjustmentsApplySignedDelta() {
		Fixture fixture = setUpFixture("inventory-balance-adjustments");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 100, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordAdjustment(fixture.item(), fixture.campus(), -10, StockAdjustmentReason.DAMAGE, LocalDate.of(2026, 1, 12), null);
		inventoryService.recordAdjustment(fixture.item(), fixture.campus(), 5, StockAdjustmentReason.COUNT_CORRECTION, LocalDate.of(2026, 1, 15), null);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(95);
	}

	@Test
	void balanceIsScopedPerCampus() {
		Fixture fixture = setUpFixture("inventory-balance-per-campus");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 100, null, LocalDate.of(2026, 1, 1), "Vendor A", null);
		inventoryService.recordEntry(fixture.item(), fixture.otherCampus(), 20, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isEqualTo(100);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.otherCampus())).isEqualTo(20);
	}
}
