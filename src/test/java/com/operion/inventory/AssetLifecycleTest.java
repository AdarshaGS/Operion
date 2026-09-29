package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
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
 * Covers #177: Asset is its own instance-tracking entity (one row per physical unit),
 * distinct from the bulk-stock ledger - creation, status lifecycle, and assignment to a
 * person and/or a free-text location.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssetLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private PersonRepository personRepository;

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
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("IT_EQUIPMENT", "IT Equipment", null));
		Item item = inventoryService.createItem(category, "LAPTOP", "Dell Latitude", "PCS", null, null);

		return new Fixture(item, campus);
	}

	@Test
	void assetIsCreatedActiveWithNoAssignment() {
		Fixture fixture = setUpFixture("asset-create");

		Asset asset = inventoryService.createAsset(fixture.item(), fixture.campus(), "LAPTOP-0001", LocalDate.of(2026, 1, 1),
				LocalDate.of(2028, 1, 1));

		assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);
		assertThat(asset.getAssignedPerson()).isNull();
		assertThat(asset.getAssignedLocation()).isNull();
		assertThat(asset.getWarrantyExpiryDate()).isEqualTo(LocalDate.of(2028, 1, 1));
	}

	@Test
	void assetStatusCanChangeThroughItsLifecycle() {
		Fixture fixture = setUpFixture("asset-status");
		Asset asset = inventoryService.createAsset(fixture.item(), fixture.campus(), "LAPTOP-0002", null, null);

		Asset underRepair = inventoryService.changeAssetStatus(asset, AssetStatus.UNDER_REPAIR);
		assertThat(underRepair.getStatus()).isEqualTo(AssetStatus.UNDER_REPAIR);

		Asset disposed = inventoryService.changeAssetStatus(underRepair, AssetStatus.DISPOSED);
		assertThat(disposed.getStatus()).isEqualTo(AssetStatus.DISPOSED);
	}

	@Test
	void assetCanBeAssignedToAPersonAndALocationIndependently() {
		Fixture fixture = setUpFixture("asset-assign");
		Asset asset = inventoryService.createAsset(fixture.item(), fixture.campus(), "LAPTOP-0003", null, null);
		Person teacher = personRepository.save(new Person("Meera", "Nair"));

		Asset assigned = inventoryService.assignAsset(asset, teacher, "Staff Room 2");

		assertThat(assigned.getAssignedPerson().getId()).isEqualTo(teacher.getId());
		assertThat(assigned.getAssignedLocation()).isEqualTo("Staff Room 2");
	}

	@Test
	void reassigningToLocationOnlyClearsThePreviousPerson() {
		Fixture fixture = setUpFixture("asset-reassign");
		Asset asset = inventoryService.createAsset(fixture.item(), fixture.campus(), "LAPTOP-0004", null, null);
		Person teacher = personRepository.save(new Person("Meera", "Nair"));
		inventoryService.assignAsset(asset, teacher, null);

		Asset reassigned = inventoryService.assignAsset(asset, null, "IT Store Room");

		assertThat(reassigned.getAssignedPerson()).isNull();
		assertThat(reassigned.getAssignedLocation()).isEqualTo("IT Store Room");
	}
}
