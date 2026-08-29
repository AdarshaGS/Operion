package com.operion.document.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.document.DocumentTemplateRepository;
import com.operion.document.DocumentType;
import com.operion.document.TemplateStyle;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Proves the GET-defaults/PUT-upsert round trip for DocumentTemplate (#31) - no row
 * exists until the first save, and GET never 404s in the meantime. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DocumentTemplateControllerTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private DocumentTemplateRepository documentTemplateRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newOrganisation() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@Test
	void getReturnsUnpersistedDefaultsWhenNoRowExists() {
		newOrganisation();
		DocumentTemplateController controller = new DocumentTemplateController(documentTemplateRepository);

		DocumentTemplateResponse response = controller.get(DocumentType.REPORT_CARD);

		assertThat(response.configured()).isFalse();
		assertThat(response.templateStyle()).isEqualTo(TemplateStyle.CLASSIC);
		assertThat(response.pageSize()).isEqualTo("A4");
	}

	@Test
	void putCreatesThenUpdatesTheSameRow() {
		newOrganisation();
		DocumentTemplateController controller = new DocumentTemplateController(documentTemplateRepository);

		DocumentTemplateResponse created = controller.update(DocumentType.QUESTION_PAPER_HEADER,
				new UpsertDocumentTemplateRequest(TemplateStyle.MODERN, "A4", "Serif", 14, "Half-Yearly Examination"));
		assertThat(created.configured()).isTrue();
		assertThat(created.templateStyle()).isEqualTo(TemplateStyle.MODERN);
		assertThat(created.headerSubtext()).isEqualTo("Half-Yearly Examination");

		DocumentTemplateResponse updated = controller.update(DocumentType.QUESTION_PAPER_HEADER,
				new UpsertDocumentTemplateRequest(TemplateStyle.MINIMAL, "Letter", "Sans-serif", 11, null));
		assertThat(updated.templateStyle()).isEqualTo(TemplateStyle.MINIMAL);
		assertThat(updated.pageSize()).isEqualTo("Letter");
		assertThat(documentTemplateRepository.findAll()).hasSize(1);

		DocumentTemplateResponse reread = controller.get(DocumentType.QUESTION_PAPER_HEADER);
		assertThat(reread.templateStyle()).isEqualTo(TemplateStyle.MINIMAL);
	}
}
