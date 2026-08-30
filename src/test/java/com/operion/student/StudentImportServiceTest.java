package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.api.StudentImportRowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the CSV batch is genuinely per-row (#28): valid rows persist and are reported,
 * an invalid row is reported as a failure without a) aborting the rows after it or
 * b) leaving behind an orphan Person from its own partial work - see
 * StudentRowImportService's REQUIRES_NEW javadoc for why that requires its own
 * transaction per row rather than one transaction for the whole batch.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentImportServiceTest.AuditLogServiceTestConfig.class, StudentService.class,
		StudentRowImportService.class, StudentImportService.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentImportServiceTest {

	/** AuditLogService.class alone can't be @Imported directly - its ObjectMapper param
	 * isn't autoconfigured under @DataJpaTest, same reason other tests in this codebase
	 * construct it manually rather than relying on Spring for it. */
	@TestConfiguration
	static class AuditLogServiceTestConfig {
		@Bean
		AuditLogService auditLogService(AuditLogRepository auditLogRepository) {
			return new AuditLogService(auditLogRepository, new ObjectMapper());
		}
	}

	@Autowired
	private StudentImportService studentImportService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void partiallyImportsAValidRowAfterAnInvalidOneAndReportsBoth() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", "iso-test-import"));
		TenantContext.set(organisation.getId(), null);

		String csv = String.join("\n",
				"firstName,lastName,dateOfBirth,gender,email,phone,admissionNumber,admissionDate,admissionSource,previousSchool,tcNumber,entranceScore,bloodGroup,category,nationality,remarks",
				"Asha,Rao,2012-04-18,FEMALE,asha@example.com,9876500000,ADM-001,2026-06-01,WALK_IN,,,,O+,General,Indian,",
				"Vikram,Singh,2011-01-01,MALE,,,,,,,,,,,,", "Ravi,Kumar,2011-01-01,MALE,ravi@example.com,9876500001,ADM-002,2026-06-01,,,,,,,,");

		MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<StudentImportRowResult> results = studentImportService.importCsv(file);

		assertThat(results).hasSize(3);
		assertThat(results.get(0).success()).isTrue();
		assertThat(results.get(0).row()).isEqualTo(2);
		assertThat(results.get(1).success()).isFalse();
		assertThat(results.get(1).row()).isEqualTo(3);
		assertThat(results.get(1).message()).contains("admissionDate");
		assertThat(results.get(2).success()).isTrue();
		assertThat(results.get(2).row()).isEqualTo(4);

		assertThat(studentRepository.findAll()).extracting(Student::getAdmissionNumber).containsExactlyInAnyOrder("ADM-001", "ADM-002");
		// The failed row's Person insert must not survive either - REQUIRES_NEW rolls
		// back the whole row, not just the half that threw.
		assertThat(personRepository.findAll()).extracting(p -> p.getFirstName()).containsExactlyInAnyOrder("Asha", "Ravi");
	}

	/** admissionNumber became optional in #142 - a blank cell auto-generates one via StudentService. */
	@Test
	void autoGeneratesAdmissionNumberWhenCsvCellIsBlank() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", "iso-test-import-auto"));
		TenantContext.set(organisation.getId(), null);

		String csv = String.join("\n",
				"firstName,lastName,dateOfBirth,gender,email,phone,admissionNumber,admissionDate,admissionSource,previousSchool,tcNumber,entranceScore,bloodGroup,category,nationality,remarks",
				"Zoya,Khan,2012-01-01,FEMALE,,,,2026-06-01,,,,,,,,");

		MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<StudentImportRowResult> results = studentImportService.importCsv(file);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).success()).isTrue();
		assertThat(studentRepository.findAll()).singleElement().extracting(Student::getAdmissionNumber).satisfies(
				admissionNumber -> assertThat((String) admissionNumber).startsWith("STU-2026-"));
	}
}
