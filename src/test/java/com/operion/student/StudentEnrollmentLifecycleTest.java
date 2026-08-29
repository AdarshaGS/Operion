package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
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

/**
 * Proves StudentService's insert-only enrollment history (mirrors
 * TeacherAssignmentReassignmentTest's pattern for TeacherAssignment): promote() closes
 * the previous current row rather than mutating its section/year, and recordExit()
 * cascades into both the enrollment and the student's own status, per
 * ai-context/erp-system-plan.md §2.2.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentEnrollmentLifecycleTest {

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
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpStudentService() {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void promotingAStudentClosesThePreviousEnrollmentAndStartsANewOne() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "promotion-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		SchoolClass class5 = schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null));
		SchoolClass class6 = schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null));
		Section section5A = sectionRepository.save(new Section(class5, "A", 40, null));
		Section section6A = sectionRepository.save(new Section(class6, "A", 40, null));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, "ADM-001", LocalDate.of(2025, 5, 1), "WALK_IN", null, null, null, null, null, "Indian", null);
		assertThat(student.getStatus()).isEqualTo(StudentStatus.ADMITTED);

		StudentEnrollment firstEnrollment =
				studentService.enroll(student, ay1, section5A, 12, LocalDate.of(2025, 6, 1));

		Student reloadedAfterEnroll = studentRepository.findById(student.getId()).orElseThrow();
		assertThat(reloadedAfterEnroll.getStatus()).isEqualTo(StudentStatus.ACTIVE);
		assertThat(firstEnrollment.isCurrent()).isTrue();
		assertThat(firstEnrollment.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ENROLLED);

		assertThatThrownBy(() -> studentService.enroll(student, ay1, section5A, 13, LocalDate.of(2025, 6, 2)))
				.isInstanceOf(IllegalStateException.class);

		StudentEnrollment promoted =
				studentService.promote(student, ay2, section6A, 5, LocalDate.of(2026, 4, 30), false);

		StudentEnrollment reloadedFirst = studentEnrollmentRepository.findById(firstEnrollment.getId()).orElseThrow();
		assertThat(reloadedFirst.isCurrent()).isFalse();
		assertThat(reloadedFirst.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.PROMOTED);
		assertThat(reloadedFirst.getExitDate()).isEqualTo(LocalDate.of(2026, 4, 30));
		assertThat(reloadedFirst.getSection().getId()).isEqualTo(section5A.getId());

		assertThat(promoted.isCurrent()).isTrue();
		assertThat(promoted.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ENROLLED);
		assertThat(promoted.getSection().getId()).isEqualTo(section6A.getId());
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.map(StudentEnrollment::getId)
				.contains(promoted.getId());
	}

	@Test
	void recordingAnExitClosesTheCurrentEnrollmentAndTransitionsTheStudent() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "exit-school"));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = personRepository.save(new Person("Rohan", "Das"));

		Student student = studentService.admit(
				person, "ADM-002", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 1, LocalDate.of(2025, 6, 1));

		studentService.recordExit(
				student, StudentExitType.WITHDRAWAL, LocalDate.of(2025, 12, 1), "Relocation", null, null);

		Student reloadedStudent = studentRepository.findById(student.getId()).orElseThrow();
		assertThat(reloadedStudent.getStatus()).isEqualTo(StudentStatus.WITHDRAWN);

		StudentEnrollment reloadedEnrollment = studentEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
		assertThat(reloadedEnrollment.isCurrent()).isFalse();
		assertThat(reloadedEnrollment.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.WITHDRAWN);
	}
}
