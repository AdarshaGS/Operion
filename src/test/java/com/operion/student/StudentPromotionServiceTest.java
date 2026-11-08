package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.common.InvalidRequestException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentPromotionServiceTest {

	private StudentService studentService;
	private StudentPromotionService promotionService;

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

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		studentService = TransactionalServiceProxy.of(new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()),
			new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper())), transactionManager);
		promotionService = new StudentPromotionService(studentService, studentRepository, academicYearRepository, sectionRepository, studentEnrollmentRepository);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void appliesEachDecisionIndependentlyAndReportsFailuresWithoutBlockingTheRest() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "bulk-promotion-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		Section section5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section nextYear5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade5, null)), "A", 40, null));
		Section nextYear6A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null)), "A", 40, null));

		Student promoted = admitAndEnroll("Meera", "ADM-101", ay1, section5A, 1);
		Student repeater = admitAndEnroll("Rohan", "ADM-102", ay1, section5A, 2);
		Student graduate = admitAndEnroll("Asha", "ADM-103", ay1, section5A, 3);
		Student missingSection = admitAndEnroll("Kiran", "ADM-104", ay1, section5A, 4);

		List<StudentPromotionService.Outcome> outcomes = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(promoted.getId(), StudentPromotionService.Action.PROMOTE, nextYear6A.getId(), 1),
				new StudentPromotionService.Decision(repeater.getId(), StudentPromotionService.Action.REPEAT, nextYear5A.getId(), 1),
				new StudentPromotionService.Decision(graduate.getId(), StudentPromotionService.Action.GRADUATE, null, null),
				new StudentPromotionService.Decision(missingSection.getId(), StudentPromotionService.Action.PROMOTE, null, null)), null);

		assertThat(outcomes).extracting(StudentPromotionService.Outcome::succeeded).containsExactly(true, true, true, false);
		assertThat(outcomes).extracting(StudentPromotionService.Outcome::skipped).containsOnly(false);
		assertThat(outcomes.get(3).error()).contains("target section");

		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(promoted.getId()))
				.map(e -> e.getSection().getId()).contains(nextYear6A.getId());
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(repeater.getId()))
				.map(e -> e.getSection().getId()).contains(nextYear5A.getId());
		assertThat(studentEnrollmentRepository.findByStudentId(repeater.getId()))
				.extracting(StudentEnrollment::getEnrollmentStatus)
				.contains(StudentEnrollmentStatus.REPEATED);
		assertThat(studentRepository.findById(graduate.getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.GRADUATED);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(graduate.getId())).isEmpty();
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(missingSection.getId()))
				.map(e -> e.getSection().getId()).contains(section5A.getId());
	}

	@Test
	void promotingOrRepeatingRequiresATargetYear() {
		assertThatThrownBy(() -> promotionService.apply(null, LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(1L, StudentPromotionService.Action.PROMOTE, 1L, null)), null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void rerunningTheSameDecisionsReportsSkippedNotAnErrorAndChangesNothing() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "promotion-rerun-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		Section section5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section nextYear6A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null)), "A", 40, null));
		Section nextYear6B = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null)), "B", 40, null));
		Student student = admitAndEnroll("Meera", "ADM-201", ay1, section5A, 1);
		List<StudentPromotionService.Decision> decisions = List.of(
				new StudentPromotionService.Decision(student.getId(), StudentPromotionService.Action.PROMOTE, nextYear6A.getId(), null));

		List<StudentPromotionService.Outcome> first = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), decisions, null);
		assertThat(first).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isTrue();
			assertThat(o.skipped()).isFalse();
			assertThat(o.error()).isNull();
		});

		List<StudentPromotionService.Outcome> rerun = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), decisions, null);
		assertThat(rerun).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isTrue();
			assertThat(o.skipped()).isTrue();
			assertThat(o.error()).isNull();
		});
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(2);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.hasValueSatisfying(e -> {
					assertThat(e.getSection().getId()).isEqualTo(nextYear6A.getId());
					assertThat(e.getRollNumber()).isEqualTo(1);
				});

		// A different placement for a student already moved is a real conflict, not "already done".
		List<StudentPromotionService.Outcome> elsewhere = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(student.getId(), StudentPromotionService.Action.PROMOTE, nextYear6B.getId(), null)), null);
		assertThat(elsewhere).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isFalse();
			assertThat(o.skipped()).isFalse();
			assertThat(o.error()).contains("different academic year");
		});
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(2);
	}

	@Test
	void aGradeSkippingDecisionIsReportedAsAFailureAndLeavesTheStudentEnrolled() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "promotion-skip-grade-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		GradeLevel grade7 = gradeLevelRepository.save(new GradeLevel("Grade 7", 7, null));
		Section section5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section nextYear7A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade7, null)), "A", 40, null));
		Student student = admitAndEnroll("Rohan", "ADM-301", ay1, section5A, 1);

		List<StudentPromotionService.Outcome> outcomes = promotionService.apply(ay2.getId(), LocalDate.of(2026, 4, 30), List.of(
				new StudentPromotionService.Decision(student.getId(), StudentPromotionService.Action.PROMOTE, nextYear7A.getId(), null)), null);

		assertThat(outcomes).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isFalse();
			assertThat(o.skipped()).isFalse();
			assertThat(o.error()).contains("must be promoted to Grade 6, not Grade 7");
		});
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.hasValueSatisfying(e -> assertThat(e.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ENROLLED));
	}

	/** Sitting in the requested section is not "already applied" unless this decision is what put the student there. */
	@Test
	void aStudentWhoGotToTheTargetSectionAnotherWayIsNotReportedAsSkipped() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "promotion-skip-tighten-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(
				new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		Section section5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section nextYear6A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade6, null)), "A", 40, null));

		// Enrolled straight into the target section - no earlier closed enrollment - so a PROMOTE decision is not "already applied".
		Student enrolledDirectly = admitAndEnroll("Direct", "ADM-401", ay2, nextYear6A, 1);
		List<StudentPromotionService.Outcome> direct = promotionService.apply(ay2.getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(enrolledDirectly.getId(), StudentPromotionService.Action.PROMOTE, nextYear6A.getId(), null)), null);
		assertThat(direct).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isFalse();
			assertThat(o.skipped()).isFalse();
			assertThat(o.error()).contains("different academic year");
		});

		// Promoted into the section, then a REPEAT decision for that same section: the earlier enrollment was PROMOTED, not REPEATED.
		Student promoted = admitAndEnroll("Moved", "ADM-402", ay1, section5A, 1);
		promotionService.apply(ay2.getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(promoted.getId(), StudentPromotionService.Action.PROMOTE, nextYear6A.getId(), 2)), null);
		List<StudentPromotionService.Outcome> wrongAction = promotionService.apply(ay2.getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(promoted.getId(), StudentPromotionService.Action.REPEAT, nextYear6A.getId(), null)), null);
		assertThat(wrongAction).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isFalse();
			assertThat(o.skipped()).isFalse();
		});
		assertThat(studentEnrollmentRepository.findByStudentId(promoted.getId())).hasSize(2);
	}

	@Test
	void rerunningAGraduateDecisionIsSkippedNotAnError() {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", "promotion-graduate-rerun-school"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade12 = gradeLevelRepository.save(new GradeLevel("Grade 12", 12, null));
		Section section12A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade12, null)), "A", 40, null));
		Student student = admitAndEnroll("Asha", "ADM-501", ay1, section12A, 1);
		List<StudentPromotionService.Decision> decisions = List.of(
				new StudentPromotionService.Decision(student.getId(), StudentPromotionService.Action.GRADUATE, null, null));

		assertThat(promotionService.apply(null, LocalDate.of(2026, 4, 30), decisions, null)).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isTrue();
			assertThat(o.skipped()).isFalse();
		});
		assertThat(promotionService.apply(null, LocalDate.of(2026, 4, 30), decisions, null)).singleElement().satisfies(o -> {
			assertThat(o.succeeded()).isTrue();
			assertThat(o.skipped()).isTrue();
			assertThat(o.error()).isNull();
		});
		assertThat(studentExitRepository.findAll()).hasSize(1);
		assertThat(studentRepository.findById(student.getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.GRADUATED);
	}

	private Student admitAndEnroll(String firstName, String admissionNumber, AcademicYear year, Section section, int roll) {
		Person person = personRepository.save(new Person(firstName, "Test"));
		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		studentService.enroll(student, year, section, roll, LocalDate.of(2025, 6, 1));
		return student;
	}
}
