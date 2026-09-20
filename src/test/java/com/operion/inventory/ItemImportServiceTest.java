package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunRepository;
import com.operion.common.imports.ImportRunService;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the shared validate-then-confirm shape for Item bulk import (Imports & exports
 * rebuild): happy-path create, a duplicate code, and an unresolved category - same
 * per-row-transaction convention as StudentImportServiceTest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, InventoryService.class, ItemRowImportService.class, ItemImportService.class,
		ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItemImportServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private ItemCategoryRepository itemCategoryRepository;

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private ItemImportService itemImportService;

	@Autowired
	private ImportRunRepository importRunRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newTenant(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
	}

	private MockMultipartFile csv(String... lines) {
		return new MockMultipartFile("file", "items.csv", "text/csv", String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void importsAValidRowAndRecordsTheRun() {
		newTenant("item-import-happy");
		itemCategoryRepository.save(new ItemCategory("STA", "Stationery", null));

		MockMultipartFile file = csv("category,code,name,unit,description,reorderLevel", "Stationery,PEN-01,Blue Pen,PCS,,10");

		List<ImportRowResult> results = itemImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(results.get(0).row()).isEqualTo(2);
		assertThat(itemRepository.findAll()).extracting(Item::getCode).containsExactly("PEN-01");
		assertThat(importRunRepository.findAllByOrderByCreatedAtDesc()).singleElement()
				.satisfies(run -> assertThat(run.getImportedCount()).isEqualTo(1));
	}

	@Test
	void duplicateCodeIsReportedAndCreatesNothing() {
		newTenant("item-import-duplicate");
		ItemCategory category = itemCategoryRepository.save(new ItemCategory("STA", "Stationery", null));
		itemRepository.save(new Item(category, "PEN-01", "Blue Pen", "PCS", null));

		MockMultipartFile file = csv("category,code,name,unit,description,reorderLevel", "Stationery,PEN-01,Blue Pen,PCS,,10");

		List<ImportRowResult> results = itemImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(itemRepository.findAll()).hasSize(1);
	}

	@Test
	void unresolvedCategoryIsReportedAsAnError() {
		newTenant("item-import-bad-category");

		MockMultipartFile file = csv("category,code,name,unit,description,reorderLevel", "Nonexistent,PEN-01,Blue Pen,PCS,,10");

		List<ImportRowResult> results = itemImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("Nonexistent");
		assertThat(itemRepository.findAll()).isEmpty();
	}

	@Test
	void validateOnlyPersistsNothing() {
		newTenant("item-import-validate-only");
		itemCategoryRepository.save(new ItemCategory("STA", "Stationery", null));

		MockMultipartFile file = csv("category,code,name,unit,description,reorderLevel", "Stationery,PEN-01,Blue Pen,PCS,,10");

		List<ImportRowResult> results = itemImportService.importFile(file, true);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(itemRepository.findAll()).isEmpty();
		assertThat(importRunRepository.findAll()).isEmpty();
	}
}
