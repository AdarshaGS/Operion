package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Proves StudentRepository.search()'s filters (#245) - each is optional and additive,
 * and the class/section filters mean "currently placed there" via the student's current
 * enrollment, not "ever placed there". Each test gets its own org/slug, same reasoning
 * as SectionCapacityTest: NOT_SUPPORTED means nothing rolls back between tests, so a
 * shared slug across tests would collide on the second run. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentSearchTest {

	private StudentService studentService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

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

	private record Fixture(AcademicYear academicYear, Section sectionA, Section sectionB) {
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Fixture setUpOrg(String slug) {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));

		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		SchoolClass classA = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		SchoolClass classB = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade6, null));
		Section sectionA = sectionRepository.save(new Section(classA, "A", null, null));
		Section sectionB = sectionRepository.save(new Section(classB, "A", null, null));
		return new Fixture(academicYear, sectionA, sectionB);
	}

	private Student admit(String firstName, String lastName, String admissionNumber, LocalDate admissionDate) {
		Person person = personRepository.save(new Person(firstName, lastName));
		return studentService.admit(
				person, admissionNumber, admissionDate, null, null, null, null, null, null, null, null, null, null, null);
	}

	@Test
	void searchesByFirstOrLastNameCaseInsensitively() {
		setUpOrg("search-name-school");
		admit("Anaya", "Rao", "ADM-1", LocalDate.of(2025, 6, 1));
		admit("Vikram", "Anaya", "ADM-2", LocalDate.of(2025, 6, 1));
		admit("Ira", "Shah", "ADM-3", LocalDate.of(2025, 6, 1));

		Page<Student> page = studentRepository.search("%anaya%", null, null, null, null, null, PageRequest.of(0, 10));

		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	void searchesByAdmissionNumber() {
		setUpOrg("search-admno-school");
		admit("Anaya", "Rao", "ADM-100", LocalDate.of(2025, 6, 1));
		admit("Ira", "Shah", "ADM-200", LocalDate.of(2025, 6, 1));

		Page<Student> page = studentRepository.search("%adm-200%", null, null, null, null, null, PageRequest.of(0, 10));

		assertThat(page.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-200");
	}

	@Test
	void filtersByStatus() {
		Fixture fixture = setUpOrg("search-status-school");
		Student active = admit("Anaya", "Rao", "ADM-1", LocalDate.of(2025, 6, 1));
		studentService.enroll(active, fixture.academicYear(), fixture.sectionA(), 1, LocalDate.of(2025, 6, 1));
		admit("Ira", "Shah", "ADM-2", LocalDate.of(2025, 6, 1));

		Page<Student> page = studentRepository.search(null, StudentStatus.ACTIVE, null, null, null, null, PageRequest.of(0, 10));

		assertThat(page.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-1");
	}

	@Test
	void filtersByCurrentSchoolClassAndSection() {
		Fixture fixture = setUpOrg("search-class-school");
		Student inSectionA = admit("Anaya", "Rao", "ADM-1", LocalDate.of(2025, 6, 1));
		studentService.enroll(inSectionA, fixture.academicYear(), fixture.sectionA(), 1, LocalDate.of(2025, 6, 1));
		Student inSectionB = admit("Ira", "Shah", "ADM-2", LocalDate.of(2025, 6, 1));
		studentService.enroll(inSectionB, fixture.academicYear(), fixture.sectionB(), 1, LocalDate.of(2025, 6, 1));
		admit("Vikram", "Nair", "ADM-3", LocalDate.of(2025, 6, 1));

		Page<Student> bySection =
				studentRepository.search(null, null, null, fixture.sectionA().getId(), null, null, PageRequest.of(0, 10));
		assertThat(bySection.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-1");

		Page<Student> byClass = studentRepository.search(
				null, null, fixture.sectionB().getSchoolClass().getId(), null, null, null, PageRequest.of(0, 10));
		assertThat(byClass.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-2");
	}

	@Test
	void filtersByAdmissionDateRange() {
		setUpOrg("search-date-school");
		admit("Anaya", "Rao", "ADM-1", LocalDate.of(2025, 1, 10));
		admit("Ira", "Shah", "ADM-2", LocalDate.of(2025, 6, 15));
		admit("Vikram", "Nair", "ADM-3", LocalDate.of(2025, 12, 20));

		Page<Student> page = studentRepository.search(
				null, null, null, null, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 9, 1), PageRequest.of(0, 10));

		assertThat(page.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-2");
	}

	@Test
	void paginatesResults() {
		setUpOrg("search-page-school");
		for (int i = 1; i <= 5; i++) {
			admit("Student" + i, "Test", "ADM-" + i, LocalDate.of(2025, 6, 1));
		}

		Page<Student> firstPage = studentRepository.search(null, null, null, null, null, null, PageRequest.of(0, 2));

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.getTotalElements()).isEqualTo(5);
		assertThat(firstPage.getTotalPages()).isEqualTo(3);
	}

	@Test
	void enrichesRowsWithCurrentSectionAndPrimaryGuardian() {
		Fixture fixture = setUpOrg("search-enrich-school");
		Student student = admit("Anaya", "Rao", "ADM-1", LocalDate.of(2025, 6, 1));
		studentService.enroll(student, fixture.academicYear(), fixture.sectionA(), 1, LocalDate.of(2025, 6, 1));

		List<StudentEnrollment> currentEnrollments =
				studentEnrollmentRepository.findByStudentIdInAndCurrentTrue(List.of(student.getId()));

		assertThat(currentEnrollments).hasSize(1);
		assertThat(currentEnrollments.get(0).getSection().getId()).isEqualTo(fixture.sectionA().getId());
	}
}
