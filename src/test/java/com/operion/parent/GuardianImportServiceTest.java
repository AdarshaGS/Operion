package com.operion.parent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the Guardian bulk import is genuinely per-row (mirrors StudentImportServiceTest
 * for #28's partial-import guarantee): a valid row links a guardian to an existing
 * student, an unknown admission number is reported rather than crashing the batch, and
 * re-importing the same row is reported as a duplicate rather than creating a second link.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class, ImportRunService.class, GuardianRowImportService.class,
		GuardianImportService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuardianImportServiceTest {

	private static final String HEADER = "studentAdmissionNumber,firstName,lastName,email,phone,occupation,relationshipType,"
			+ "isPrimary,isEmergencyContact,canPickup,canReceiveCommunication,contactPriority";

	@Autowired
	private GuardianImportService guardianImportService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentGuardianRepository studentGuardianRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Student seedStudent(String admissionNumber) {
		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		return studentRepository.save(new Student(studentPerson, "STU-" + admissionNumber, admissionNumber,
				LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null, null));
	}

	private MockMultipartFile csv(String... rows) {
		String content = String.join("\n", rows);
		return new MockMultipartFile("file", "guardians.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void importsAValidRowAndLinksTheGuardianToTheStudent() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", "iso-guardian-import"));
		TenantContext.set(organisation.getId(), null);
		Student student = seedStudent("ADM-100");

		List<ImportRowResult> results = guardianImportService.importFile(
				csv(HEADER, "ADM-100,Vikram,Shah,vikram@example.com,9876500000,Engineer,FATHER,true,true,true,true,1"), false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).row()).isEqualTo(2);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(results.get(0).id()).isNotNull();

		List<StudentGuardian> links = studentGuardianRepository.findByStudentId(student.getId());
		assertThat(links).hasSize(1);
		assertThat(links.get(0).getGuardian().getPerson().getEmail()).isEqualTo("vikram@example.com");
		assertThat(links.get(0).getRelationshipType()).isEqualTo(GuardianRelationshipType.FATHER);
	}

	@Test
	void reportsAnUnknownAdmissionNumberAsAnErrorInsteadOfCrashing() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", "iso-guardian-unknown"));
		TenantContext.set(organisation.getId(), null);

		List<ImportRowResult> results = guardianImportService.importFile(
				csv(HEADER, "ADM-404,Vikram,Shah,vikram@example.com,9876500000,Engineer,FATHER,true,true,true,true,1"), false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("ADM-404");
	}

	@Test
	void reimportingTheSameRowReportsDuplicateAndDoesNotCreateASecondLink() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", "iso-guardian-dup"));
		TenantContext.set(organisation.getId(), null);
		Student student = seedStudent("ADM-200");

		String row = "ADM-200,Vikram,Shah,vikram@example.com,9876500000,Engineer,FATHER,true,true,true,true,1";
		guardianImportService.importFile(csv(HEADER, row), false);

		List<ImportRowResult> secondResults = guardianImportService.importFile(csv(HEADER, row), false);

		assertThat(secondResults).hasSize(1);
		assertThat(secondResults.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(studentGuardianRepository.findByStudentId(student.getId())).hasSize(1);
	}
}
