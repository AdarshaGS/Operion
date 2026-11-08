package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
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

/** Proves StudentRepository.search()'s campusId filter (the top-bar campus on the student
 * list): it matches the campus of the student's CURRENT enrollment's class, null means no
 * filtering (students with no enrollment stay), and another organisation's campus id matches
 * nothing because the tenant filter still scopes every row. Same per-test org/slug setup as
 * StudentSearchTest, for the same NOT_SUPPORTED reason. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentSearchCampusTest {

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

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	/** One org with two campuses; main has two students, branch one, and one student is unplaced. */
	private record Fixture(Campus main, Campus branch) {
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void initService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator,
				new AuditLogService(auditLogRepository, new ObjectMapper()),
				new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
	}

	private Organisation enterOrg(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}

	private Student admit(String firstName, String lastName, String admissionNumber) {
		Person person = personRepository.save(new Person(firstName, lastName));
		return studentService.admit(person, admissionNumber, LocalDate.of(2025, 6, 1), null, null, null, null, null, null,
				null, null, null, null, null);
	}

	private Section sectionOn(AcademicYear year, Campus campus, GradeLevel grade) {
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		return sectionRepository.save(new Section(schoolClass, "A", null, null));
	}

	private Fixture setUpTwoCampusOrg(String slug) {
		initService();
		enterOrg(slug);
		AcademicYear year =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus main = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Campus branch = campusRepository.save(new Campus("Branch Campus", "BRANCH"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		Section mainGrade5 = sectionOn(year, main, grade5);
		Section mainGrade6 = sectionOn(year, main, grade6);
		Section branchGrade5 = sectionOn(year, branch, grade5);

		studentService.enroll(admit("Anaya", "Rao", "ADM-1"), year, mainGrade5, 1, LocalDate.of(2025, 6, 1));
		studentService.enroll(admit("Ira", "Shah", "ADM-2"), year, mainGrade6, 1, LocalDate.of(2025, 6, 1));
		studentService.enroll(admit("Vikram", "Nair", "ADM-3"), year, branchGrade5, 1, LocalDate.of(2025, 6, 1));
		admit("Zoya", "Khan", "ADM-4"); // admitted, never placed in a section
		return new Fixture(main, branch);
	}

	@Test
	void campusFilterReturnsOnlyStudentsEnrolledInThatCampusesClasses() {
		Fixture fixture = setUpTwoCampusOrg("search-campus-filter-school");

		Page<Student> main = studentRepository.search(null, null, null, null, fixture.main().getId(), null, null,
				PageRequest.of(0, 10));
		Page<Student> branch = studentRepository.search(null, null, null, null, fixture.branch().getId(), null, null,
				PageRequest.of(0, 10));

		assertThat(main.getContent()).extracting(Student::getAdmissionNumber).containsExactlyInAnyOrder("ADM-1", "ADM-2");
		assertThat(main.getTotalElements()).isEqualTo(2);
		assertThat(branch.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-3");
		assertThat(branch.getTotalElements()).isEqualTo(1);
	}

	@Test
	void nullCampusReturnsEveryoneIncludingStudentsWithNoEnrollment() {
		setUpTwoCampusOrg("search-campus-null-school");

		Page<Student> all = studentRepository.search(null, null, null, null, null, null, null, PageRequest.of(0, 10));
		Page<Student> viaOldSignature = studentRepository.search(null, null, null, null, null, null, PageRequest.of(0, 10));

		assertThat(all.getContent()).extracting(Student::getAdmissionNumber)
				.containsExactlyInAnyOrder("ADM-1", "ADM-2", "ADM-3", "ADM-4");
		assertThat(all.getTotalElements()).isEqualTo(4);
		assertThat(viaOldSignature.getTotalElements()).isEqualTo(4);
	}

	@Test
	void campusFilterComposesWithOtherFiltersAndKeepsCountConsistentWhilePaging() {
		Fixture fixture = setUpTwoCampusOrg("search-campus-compose-school");

		Page<Student> firstPage = studentRepository.search(null, null, null, null, fixture.main().getId(), null, null,
				PageRequest.of(0, 1));
		Page<Student> searched = studentRepository.search("%ira%", null, null, null, fixture.main().getId(), null, null,
				PageRequest.of(0, 10));
		Page<Student> searchedOtherCampus = studentRepository.search("%ira%", null, null, null, fixture.branch().getId(),
				null, null, PageRequest.of(0, 10));

		assertThat(firstPage.getContent()).hasSize(1);
		assertThat(firstPage.getTotalElements()).isEqualTo(2);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(searched.getContent()).extracting(Student::getAdmissionNumber).containsExactly("ADM-2");
		assertThat(searchedOtherCampus.getTotalElements()).isZero();
	}

	@Test
	void anotherOrganisationsCampusIdMatchesNothing() {
		setUpTwoCampusOrg("search-campus-org-a-school");
		TenantContext.clear();

		// A second organisation with its own campus and an enrolled student.
		enterOrg("search-campus-org-b-school");
		AcademicYear yearB =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campusB = campusRepository.save(new Campus("Other Campus", "OTHER"));
		GradeLevel gradeB = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		studentService.enroll(admit("Other", "Student", "ADM-B1"), yearB, sectionOn(yearB, campusB, gradeB), 1,
				LocalDate.of(2025, 6, 1));
		Long orgBCampusId = campusB.getId();

		// Back in org A, org B's campus id must match no one - neither A's students nor B's.
		TenantContext.clear();
		Organisation orgA = organisationRepository.findBySlug("search-campus-org-a-school").orElseThrow();
		TenantContext.set(orgA.getId(), null);

		Page<Student> page = studentRepository.search(null, null, null, null, orgBCampusId, null, null, PageRequest.of(0, 10));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
	}
}
