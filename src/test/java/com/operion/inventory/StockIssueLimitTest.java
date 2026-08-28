package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Proves InventoryService blocks issues/adjustments that would take the computed
 * balance negative - you can't issue or write off more physical stock than exists.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockIssueLimitTest {

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

	private record Fixture(Item item, Campus campus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		return new Fixture(item, campus);
	}

	@Test
	void issuingMoreThanOnHandIsRejected() {
		Fixture fixture = setUpFixture("inventory-issue-over-limit");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 10, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		assertThatThrownBy(() -> inventoryService.recordIssue(fixture.item(), fixture.campus(), 11, LocalDate.of(2026, 1, 5), "Class 5B", null, null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void issuingExactlyOnHandIsAllowed() {
		Fixture fixture = setUpFixture("inventory-issue-exact-limit");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 10, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		inventoryService.recordIssue(fixture.item(), fixture.campus(), 10, LocalDate.of(2026, 1, 5), "Class 5B", null, null);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.campus())).isZero();
	}

	@Test
	void adjustmentThatWouldGoNegativeIsRejected() {
		Fixture fixture = setUpFixture("inventory-adjustment-over-limit");
		inventoryService.recordEntry(fixture.item(), fixture.campus(), 5, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		assertThatThrownBy(() -> inventoryService.recordAdjustment(
				fixture.item(), fixture.campus(), -6, StockAdjustmentReason.LOSS, LocalDate.of(2026, 1, 10), null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void issuingFromAnEmptyBalanceIsRejected() {
		Fixture fixture = setUpFixture("inventory-issue-empty");

		assertThatThrownBy(() -> inventoryService.recordIssue(fixture.item(), fixture.campus(), 1, LocalDate.of(2026, 1, 5), "Class 5B", null, null))
				.isInstanceOf(IllegalStateException.class);
	}
}
