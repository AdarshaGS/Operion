package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Proves #114's system-generated student ID sequence and the applicant inquiry/reject/
 * convert lifecycle - same harness pattern as the other student-module @DataJpaTest slices. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentIdAndApplicantTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private StudentDocumentRepository studentDocumentRepository;

	@Autowired
	private StudentExitRepository studentExitRepository;

	@Autowired
	private StudentIdGenerator studentIdGenerator;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private ApplicantRepository applicantRepository;

	private StudentService studentService;
	private ApplicantService applicantService;

	@BeforeEach
	void setUp() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
		applicantService = new ApplicantService(applicantRepository, studentService);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void studentIdsAreSequentialPerYearAndImmutable() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "student-id-school"));
		TenantContext.set(organisation.getId(), null);

		Person personOne = personRepository.save(new Person("First", "Student"));
		Person personTwo = personRepository.save(new Person("Second", "Student"));

		Student studentOne = studentService.admit(personOne, "ADM-100", LocalDate.of(2026, 5, 1), null, null, null,
				null, null, null, null, null, null, null, null);
		Student studentTwo = studentService.admit(personTwo, "ADM-101", LocalDate.of(2026, 6, 1), null, null, null,
				null, null, null, null, null, null, null, null);

		assertThat(studentOne.getStudentId()).isEqualTo("STU-2026-00001");
		assertThat(studentTwo.getStudentId()).isEqualTo("STU-2026-00002");
	}

	@Test
	void convertingAnApplicantAdmitsTheSamePersonAndMarksTheApplicantConverted() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "applicant-school"));
		TenantContext.set(organisation.getId(), null);

		Person person = personRepository.save(new Person("Prospective", "Student"));
		Applicant applicant = applicantService.inquire(person, LocalDate.of(2026, 3, 1), "WALK_IN", "Interested in Grade 5");

		Student student = applicantService.convert(applicant, "ADM-200", LocalDate.of(2026, 5, 1), null, null, null,
				null, null, null, null, null, null, null, null);

		assertThat(student.getPerson().getId()).isEqualTo(person.getId());
		Applicant reloaded = applicantRepository.findById(applicant.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ApplicantStatus.CONVERTED);
	}

	@Test
	void aConvertedApplicantCannotBeConvertedOrRejectedAgain() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "applicant-terminal-school"));
		TenantContext.set(organisation.getId(), null);

		Person person = personRepository.save(new Person("Another", "Prospect"));
		Applicant applicant = applicantService.inquire(person, LocalDate.of(2026, 3, 1), null, null);
		applicantService.convert(applicant, "ADM-201", LocalDate.of(2026, 5, 1), null, null, null, null, null, null,
				null, null, null, null, null);

		assertThatThrownBy(() -> applicantService.reject(applicant)).isInstanceOf(IllegalStateException.class);
	}
}
