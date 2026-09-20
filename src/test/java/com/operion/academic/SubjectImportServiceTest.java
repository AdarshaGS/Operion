package com.operion.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
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
 * Same per-row isolation proof as StudentImportServiceTest/ClassSectionImportServiceTest,
 * for the Subjects importer: a new subject is created, a repeat of the same code is
 * reported as a duplicate without creating anything, and a missing required field is
 * reported as an error rather than aborting the batch.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, AcademicService.class, SubjectRowImportService.class,
		SubjectImportService.class, ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubjectImportServiceTest {

	private static final String HEADER = "name,code";

	@Autowired
	private SubjectImportService subjectImportService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void createsASubjectOnAValidRow() {
		seedOrganisation("iso-subject-happy");

		String csv = String.join("\n", HEADER, "Mathematics,MATH");
		MockMultipartFile file = new MockMultipartFile("file", "subjects.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = subjectImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(subjectRepository.findAll()).extracting(Subject::getCode).containsExactly("MATH");
	}

	@Test
	void reportsAnExistingCodeAsADuplicateAndCreatesNothing() {
		seedOrganisation("iso-subject-duplicate");
		subjectRepository.save(new Subject("Mathematics", "MATH"));

		String csv = String.join("\n", HEADER, "Maths Advanced,math");
		MockMultipartFile file = new MockMultipartFile("file", "subjects.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = subjectImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(subjectRepository.findAll()).hasSize(1);
	}

	@Test
	void reportsAMissingNameAsAnError() {
		seedOrganisation("iso-subject-error");

		String csv = String.join("\n", HEADER, ",SCI");
		MockMultipartFile file = new MockMultipartFile("file", "subjects.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = subjectImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("name");
		assertThat(subjectRepository.findAll()).isEmpty();
	}

	private void seedOrganisation(String isoCode) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", isoCode));
		TenantContext.set(organisation.getId(), null);
	}
}
