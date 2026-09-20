package com.operion.library;

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
 * Covers the shared validate-then-confirm shape for Book bulk import (Imports & exports
 * rebuild): happy-path create, a duplicate isbn, and a missing required title - same
 * per-row-transaction convention as StudentImportServiceTest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LibraryService.class, BookRowImportService.class, BookImportService.class,
		ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookImportServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private BookImportService bookImportService;

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
		return new MockMultipartFile("file", "books.csv", "text/csv", String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void importsAValidRowAndRecordsTheRun() {
		newTenant("book-import-happy");

		MockMultipartFile file = csv("isbn,title,author,publisher,category,edition",
				"978-0-13-468599-1,Effective Java,Joshua Bloch,Addison-Wesley,Reference,3rd");

		List<ImportRowResult> results = bookImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(bookRepository.findAll()).extracting(Book::getTitle).containsExactly("Effective Java");
		assertThat(importRunRepository.findAllByOrderByCreatedAtDesc()).singleElement()
				.satisfies(run -> assertThat(run.getImportedCount()).isEqualTo(1));
	}

	@Test
	void duplicateIsbnIsReportedAndCreatesNothing() {
		newTenant("book-import-duplicate");
		bookRepository.save(new Book("978-0-13-468599-1", "Effective Java", "Joshua Bloch", "Addison-Wesley", "Reference", "3rd"));

		MockMultipartFile file = csv("isbn,title,author,publisher,category,edition",
				"978-0-13-468599-1,Effective Java,Joshua Bloch,Addison-Wesley,Reference,3rd");

		List<ImportRowResult> results = bookImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(bookRepository.findAll()).hasSize(1);
	}

	@Test
	void missingTitleIsReportedAsAnError() {
		newTenant("book-import-missing-title");

		MockMultipartFile file = csv("isbn,title,author,publisher,category,edition", "978-0-13-468599-1,,Joshua Bloch,Addison-Wesley,Reference,3rd");

		List<ImportRowResult> results = bookImportService.importFile(file, false);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("title");
		assertThat(bookRepository.findAll()).isEmpty();
	}

	@Test
	void validateOnlyPersistsNothing() {
		newTenant("book-import-validate-only");

		MockMultipartFile file = csv("isbn,title,author,publisher,category,edition",
				"978-0-13-468599-1,Effective Java,Joshua Bloch,Addison-Wesley,Reference,3rd");

		List<ImportRowResult> results = bookImportService.importFile(file, true);

		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(bookRepository.findAll()).isEmpty();
		assertThat(importRunRepository.findAll()).isEmpty();
	}
}
