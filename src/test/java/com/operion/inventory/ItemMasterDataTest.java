package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * Covers #173: Item's optional storage location and default supplier, StockEntry's
 * optional structured Supplier link alongside the existing free-text source, and
 * StockIssue's optional issue-cost field.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItemMasterDataTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private InventoryService inventoryService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(ItemCategory category, Campus campus, Supplier supplier) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STATIONERY", "Stationery", null));
		Supplier supplier = supplierRepository.save(new Supplier("Staples & Co", null, null, null, null));

		return new Fixture(category, campus, supplier);
	}

	@Test
	void itemCanBeCreatedWithStorageLocationAndDefaultSupplier() {
		Fixture fixture = setUpFixture("item-storage-supplier");

		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null,
				"Central Store - Rack 3", fixture.supplier());

		assertThat(item.getStorageLocation()).isEqualTo("Central Store - Rack 3");
		assertThat(item.getDefaultSupplier().getId()).isEqualTo(fixture.supplier().getId());
	}

	@Test
	void itemWithoutStorageLocationOrSupplierStaysNull() {
		Fixture fixture = setUpFixture("item-no-storage-supplier");

		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		assertThat(item.getStorageLocation()).isNull();
		assertThat(item.getDefaultSupplier()).isNull();
	}

	@Test
	void storageLocationAndDefaultSupplierCanBeUpdatedAfterCreation() {
		Fixture fixture = setUpFixture("item-update-storage-supplier");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		Item updated = inventoryService.updateStorageLocation(item, "Block B - Shelf 2");
		updated = inventoryService.updateDefaultSupplier(updated, fixture.supplier());

		assertThat(updated.getStorageLocation()).isEqualTo("Block B - Shelf 2");
		assertThat(updated.getDefaultSupplier().getId()).isEqualTo(fixture.supplier().getId());
	}

	@Test
	void stockEntryKeepsFreeTextSourceAlongsideStructuredSupplier() {
		Fixture fixture = setUpFixture("stock-entry-supplier");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null);

		StockEntry entryWithSupplier = inventoryService.recordEntry(item, fixture.campus(), 10, new BigDecimal("5.00"),
				LocalDate.of(2026, 1, 1), "Staples & Co", fixture.supplier(), null);
		StockEntry entryWithFreeTextOnly = inventoryService.recordEntry(item, fixture.campus(), 5, new BigDecimal("4.50"),
				LocalDate.of(2026, 1, 2), "Donation", null);

		assertThat(entryWithSupplier.getSource()).isEqualTo("Staples & Co");
		assertThat(entryWithSupplier.getSupplier().getId()).isEqualTo(fixture.supplier().getId());
		assertThat(entryWithFreeTextOnly.getSource()).isEqualTo("Donation");
		assertThat(entryWithFreeTextOnly.getSupplier()).isNull();
	}

	@Test
	void stockIssueCapturesOptionalUnitCost() {
		Fixture fixture = setUpFixture("stock-issue-unit-cost");
		Item item = inventoryService.createItem(fixture.category(), "ITEM-001", "A4 Paper Ream", "REAM", null, null);
		inventoryService.recordEntry(item, fixture.campus(), 10, null, LocalDate.of(2026, 1, 1), "Vendor A", null);

		StockIssue issueWithCost = inventoryService.recordIssue(item, fixture.campus(), 4, LocalDate.of(2026, 1, 5), "Class 5B", "Classwork",
				new BigDecimal("6.00"), null);
		StockIssue issueWithoutCost = inventoryService.recordIssue(item, fixture.campus(), 2, LocalDate.of(2026, 1, 6), "Class 5B", "Classwork",
				null);

		assertThat(issueWithCost.getUnitCost()).isEqualByComparingTo("6.00");
		assertThat(issueWithoutCost.getUnitCost()).isNull();
	}
}
