package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.common.InvalidRequestException;
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
 * Covers #175: InventoryService.recordTransfer moves balance between two campuses as
 * one atomic operation (a TRANSFER_OUT + TRANSFER_IN StockAdjustment pair, same split as
 * PurchaseReturn) and rejects a transfer the source campus can't cover.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StockTransferTest {

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

	private record Fixture(Item item, Campus fromCampus, Campus toCampus) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus fromCampus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus toCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Item item = inventoryService.createItem(category, "ITEM-001", "A4 Paper Ream", "REAM", null, null);
		inventoryService.recordEntry(item, fromCampus, 20, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		return new Fixture(item, fromCampus, toCampus);
	}

	@Test
	void transferMovesBalanceFromSourceToDestination() {
		Fixture fixture = setUpFixture("stock-transfer-basic");

		StockTransfer transfer = inventoryService.recordTransfer(fixture.item(), fixture.fromCampus(), fixture.toCampus(), 8,
				LocalDate.of(2026, 1, 10), "Redistribution");

		assertThat(transfer.getQuantity()).isEqualTo(8);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.fromCampus())).isEqualTo(12);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.toCampus())).isEqualTo(8);
	}

	@Test
	void transferExceedingSourceBalanceIsRejectedAndLeavesBalancesUnchanged() {
		Fixture fixture = setUpFixture("stock-transfer-insufficient");

		assertThatThrownBy(
				() -> inventoryService.recordTransfer(fixture.item(), fixture.fromCampus(), fixture.toCampus(), 21, LocalDate.of(2026, 1, 10), null))
				.isInstanceOf(IllegalStateException.class);

		assertThat(inventoryService.getBalance(fixture.item(), fixture.fromCampus())).isEqualTo(20);
		assertThat(inventoryService.getBalance(fixture.item(), fixture.toCampus())).isEqualTo(0);
	}

	@Test
	void transferToTheSameCampusIsRejected() {
		Fixture fixture = setUpFixture("stock-transfer-same-campus");

		assertThatThrownBy(
				() -> inventoryService.recordTransfer(fixture.item(), fixture.fromCampus(), fixture.fromCampus(), 5, LocalDate.of(2026, 1, 10), null))
				.isInstanceOf(InvalidRequestException.class);
	}
}
