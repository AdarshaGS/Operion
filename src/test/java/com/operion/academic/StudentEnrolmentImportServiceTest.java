package com.operion.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
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
 * Same per-row isolation proof as StudentImportServiceTest, for the Student Enrolments
 * importer: a student with no current enrollment gets enrolled, a student who already
 * has one is reported as a duplicate without touching it, and an unresolvable admission
 * number is reported as an error rather than aborting the batch.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentEnrolmentImportServiceTest.AuditLogServiceTestConfig.class,
		StudentService.class, StudentIdGenerator.class, StudentEnrolmentRowImportService.class,
		StudentEnrolmentImportService.class, ImportRunService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentEnrolmentImportServiceTest {

	private static final String HEADER = "studentAdmissionNumber,academicYear,className,sectionName,rollNumber,enrolledDate";

	@TestConfiguration
	static class AuditLogServiceTestConfig {
		@Bean
		AuditLogService auditLogService(AuditLogRepository auditLogRepository) {
			return new AuditLogService(auditLogRepository, new ObjectMapper());
		}
	}

	@Autowired
	private StudentEnrolmentImportService studentEnrolmentImportService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentService studentService;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void enrolsAStudentWithNoCurrentEnrollment() {
		seedOrganisation("iso-enrolment-happy");
		Section section = seedSection();
		Student student = admitStudent("ADM-100");

		String csv = String.join("\n", HEADER, "ADM-100,2025-2026,Grade 5,A,12,2025-06-01");
		MockMultipartFile file = new MockMultipartFile("file", "enrolments.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = studentEnrolmentImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId())).isPresent()
				.get().satisfies(enrollment -> {
					assertThat(enrollment.getSection().getId()).isEqualTo(section.getId());
					assertThat(enrollment.getRollNumber()).isEqualTo(12);
				});
	}

	@Test
	void reportsAStudentWithAnExistingCurrentEnrollmentAsADuplicate() {
		seedOrganisation("iso-enrolment-duplicate");
		Section section = seedSection();
		Student student = admitStudent("ADM-200");
		studentService.enroll(student, academicYearRepository.findByNameIgnoreCase("2025-2026").orElseThrow(), section, 1,
				LocalDate.of(2025, 6, 1));

		String csv = String.join("\n", HEADER, "ADM-200,2025-2026,Grade 5,A,2,2025-06-02");
		MockMultipartFile file = new MockMultipartFile("file", "enrolments.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = studentEnrolmentImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
	}

	@Test
	void reportsAnUnresolvableAdmissionNumberAsAnError() {
		seedOrganisation("iso-enrolment-error");
		seedSection();

		String csv = String.join("\n", HEADER, "NO-SUCH-ADM,2025-2026,Grade 5,A,1,2025-06-01");
		MockMultipartFile file = new MockMultipartFile("file", "enrolments.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		List<ImportRowResult> results = studentEnrolmentImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("NO-SUCH-ADM");
		assertThat(studentEnrollmentRepository.findAll()).isEmpty();
	}

	private void seedOrganisation(String isoCode) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test Trust", isoCode));
		TenantContext.set(organisation.getId(), null);
	}

	private Section seedSection() {
		AcademicYear academicYear = academicYearRepository
				.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel gradeLevel = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "Primary"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, gradeLevel, "Grade 5"));
		return sectionRepository.save(new Section(schoolClass, "A", 40, "Room 101"));
	}

	private Student admitStudent(String admissionNumber) {
		Person person = personRepository.save(new Person("Asha", "Rao"));
		return studentService.admit(person, admissionNumber, LocalDate.of(2020, 6, 1), null, null, null, null, null, null, null,
				null, null, null, null);
	}
}
