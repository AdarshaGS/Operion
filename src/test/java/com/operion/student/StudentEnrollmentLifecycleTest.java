package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
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
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
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
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
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
	private StudentIdGenerator studentIdGenerator;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUpStudentService() {
		studentService = TransactionalServiceProxy.of(new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()),
			new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper())), transactionManager);
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
				person, "ADM-001", LocalDate.of(2025, 5, 1), "WALK_IN", null, null, null, null, null, "Indian", null, null, null, null);
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

	/** Two academic years, grades 5 / 7 / 8 (sequence_order has a gap at 6), and a section per grade in each year. */
	private PromotionWorld promotionWorld(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		AcademicYear ay1 = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear ay2 = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		GradeLevel grade7 = gradeLevelRepository.save(new GradeLevel("Grade 7", 7, null));
		GradeLevel grade8 = gradeLevelRepository.save(new GradeLevel("Grade 8", 8, null));
		Section from5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay1, campus, grade5, null)), "A", 40, null));
		Section to5A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade5, null)), "A", 40, null));
		Section to7A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade7, null)), "A", 40, null));
		Section to8A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay2, campus, grade8, null)), "A", 40, null));
		return new PromotionWorld(ay1, ay2, from5A, to5A, to7A, to8A);
	}

	private record PromotionWorld(AcademicYear ay1, AcademicYear ay2, Section from5A, Section to5A, Section to7A, Section to8A) {
	}

	private Student admitAndEnroll(String admissionNumber, AcademicYear year, Section section, Integer roll) {
		Person person = personRepository.save(new Person("Student" + admissionNumber, "Test"));
		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		studentService.enroll(student, year, section, roll, LocalDate.of(2025, 6, 1));
		return student;
	}

	private void assertStillEnrolledIn(Student student, Section section, int rollNumber) {
		assertStillEnrolledIn(student, section);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.hasValueSatisfying(e -> assertThat(e.getRollNumber()).isEqualTo(rollNumber));
	}

	private void assertStillEnrolledIn(Student student, Section section) {
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.hasValueSatisfying(e -> {
					assertThat(e.getSection().getId()).isEqualTo(section.getId());
					assertThat(e.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ENROLLED);
					assertThat(e.getExitDate()).isNull();
				});
	}

	@Test
	void promoteRejectsAClosedTargetYearAndLeavesTheCurrentEnrollmentUntouched() {
		PromotionWorld w = promotionWorld("promote-closed-target-school");
		Student student = admitAndEnroll("ADM-P-1", w.ay1(), w.from5A(), 1);
		w.ay2().close();
		AcademicYear closedAy2 = academicYearRepository.save(w.ay2());

		assertThatThrownBy(() -> studentService.promote(student, closedAy2, w.to7A(), null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("closed");
		assertStillEnrolledIn(student, w.from5A());
	}

	@Test
	void promoteRejectsTargetingTheSameYearAsTheCurrentEnrollment() {
		PromotionWorld w = promotionWorld("promote-same-year-school");
		Student student = admitAndEnroll("ADM-P-2", w.ay1(), w.from5A(), 1);
		// A grade-5 class in the same year, so only the year-boundary rule can reject this REPEAT.
		Campus campus = campusRepository.findAll().get(0);
		GradeLevel grade5 = w.from5A().getSchoolClass().getGradeLevel();
		Section sameYear5B = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(w.ay1(), campus, grade5, null)), "B", 40, null));

		assertThatThrownBy(() -> studentService.promote(student, w.ay1(), sameYear5B, null, LocalDate.of(2026, 4, 30), true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("different academic year");
		assertStillEnrolledIn(student, w.from5A());
	}

	@Test
	void promoteMustLandInTheNextGradeEvenWhenSequenceOrderHasGaps() {
		PromotionWorld w = promotionWorld("promote-next-grade-school");
		Student student = admitAndEnroll("ADM-P-3", w.ay1(), w.from5A(), 1);

		// Grade 8 skips Grade 7.
		assertThatThrownBy(() -> studentService.promote(student, w.ay2(), w.to8A(), null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be promoted to Grade 7, not Grade 8");
		assertStillEnrolledIn(student, w.from5A());

		// Staying in Grade 5 is not a promotion.
		assertThatThrownBy(() -> studentService.promote(student, w.ay2(), w.to5A(), null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be promoted to Grade 7, not Grade 5");
		assertStillEnrolledIn(student, w.from5A());

		// Grade 7 is the next grade after 5 (orders 5 -> 7, no Grade 6 exists), not "order + 1".
		StudentEnrollment promoted = studentService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false);
		assertThat(promoted.getSection().getId()).isEqualTo(w.to7A().getId());
	}

	/** Rolling back on the exception would also leave the row untouched, so this checks the stronger thing: a rejection happens before the current enrollment is closed or anything is saved. */
	@Test
	void promoteRejectionsHappenBeforeAnythingIsSaved() {
		PromotionWorld w = promotionWorld("promote-validate-first-school");
		Student student = admitAndEnroll("ADM-P-11", w.ay1(), w.from5A(), 1);
		StudentEnrollmentRepository watched = mock(StudentEnrollmentRepository.class, AdditionalAnswers.delegatesTo(studentEnrollmentRepository));
		// promote() takes the section lock first, which needs a transaction - hence the proxy.
		StudentService watchedService = TransactionalServiceProxy.of(new StudentService(studentRepository, watched, studentDocumentRepository,
				studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator,
				new AuditLogService(auditLogRepository, new ObjectMapper()),
				new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper())), transactionManager);

		assertThatThrownBy(() -> watchedService.promote(student, w.ay2(), w.to8A(), null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> watchedService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), true))
				.isInstanceOf(IllegalStateException.class);

		verify(watched, never()).save(any(StudentEnrollment.class));
		assertStillEnrolledIn(student, w.from5A());
	}

	@Test
	void promoteFromTheLastGradeIsRejected() {
		PromotionWorld w = promotionWorld("promote-last-grade-school");
		AcademicYear ay3 = academicYearRepository.save(new AcademicYear("2027-2028", LocalDate.of(2027, 6, 1), LocalDate.of(2028, 4, 30)));
		Campus campus = campusRepository.findAll().get(0);
		GradeLevel grade8 = w.to8A().getSchoolClass().getGradeLevel();
		Section ay3Grade8 = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(ay3, campus, grade8, null)), "A", 40, null));
		Student eighth = admitAndEnroll("ADM-P-4", w.ay2(), w.to8A(), 1);

		assertThatThrownBy(() -> studentService.promote(eighth, ay3, ay3Grade8, null, LocalDate.of(2027, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("last grade");
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(eighth.getId()))
				.map(e -> e.getSection().getId()).contains(w.to8A().getId());
	}

	@Test
	void repeatMustStayInTheSameGrade() {
		PromotionWorld w = promotionWorld("promote-repeat-grade-school");
		Student student = admitAndEnroll("ADM-P-6", w.ay1(), w.from5A(), 1);

		assertThatThrownBy(() -> studentService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), true))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("repeating Grade 5 must be placed in Grade 5, not Grade 7");
		assertStillEnrolledIn(student, w.from5A());

		StudentEnrollment repeated = studentService.promote(student, w.ay2(), w.to5A(), null, LocalDate.of(2026, 4, 30), true);
		assertThat(repeated.getSection().getId()).isEqualTo(w.to5A().getId());
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId()))
				.extracting(StudentEnrollment::getEnrollmentStatus)
				.contains(StudentEnrollmentStatus.REPEATED);
	}

	@Test
	void promoteAutoAssignsTheNextFreeRollNumberAndKeepsExplicitOnes() {
		PromotionWorld w = promotionWorld("promote-roll-number-school");
		Student first = admitAndEnroll("ADM-P-7", w.ay1(), w.from5A(), 1);
		Student second = admitAndEnroll("ADM-P-8", w.ay1(), w.from5A(), 2);
		Student third = admitAndEnroll("ADM-P-9", w.ay1(), w.from5A(), 3);

		StudentEnrollment explicit = studentService.promote(first, w.ay2(), w.to7A(), 7, LocalDate.of(2026, 4, 30), false);
		StudentEnrollment auto = studentService.promote(second, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false);
		StudentEnrollment autoAgain = studentService.promote(third, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false);

		assertThat(explicit.getRollNumber()).isEqualTo(7);
		assertThat(auto.getRollNumber()).isEqualTo(8);
		assertThat(autoAgain.getRollNumber()).isEqualTo(9);
	}

	@Test
	void promoteAutoAssignsRollNumberOneInAnEmptySection() {
		PromotionWorld w = promotionWorld("promote-roll-number-empty-school");
		Student student = admitAndEnroll("ADM-P-10", w.ay1(), w.from5A(), 1);

		StudentEnrollment promoted = studentService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false);

		assertThat(promoted.getRollNumber()).isEqualTo(1);
	}

	@Test
	void promoteRejectsWhenTheStudentAlreadyHasAnEnrollmentInTheTargetYear() {
		PromotionWorld w = promotionWorld("promote-target-year-taken-school");
		Student student = admitAndEnroll("ADM-P-12", w.ay1(), w.from5A(), 1);
		// A closed (withdrawn) enrollment in the target year already exists for the student.
		StudentEnrollment inTarget = studentEnrollmentRepository.save(
				new StudentEnrollment(student, w.ay2(), w.to5A(), 1, LocalDate.of(2026, 6, 1)));
		inTarget.close(StudentEnrollmentStatus.WITHDRAWN, LocalDate.of(2026, 7, 1));
		studentEnrollmentRepository.save(inTarget);

		assertThatThrownBy(() -> studentService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 8, 1), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("already has an enrollment in academic year");

		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()))
				.hasValueSatisfying(e -> {
					assertThat(e.getAcademicYear().getId()).isEqualTo(w.ay1().getId());
					assertThat(e.getEnrollmentStatus()).isEqualTo(StudentEnrollmentStatus.ENROLLED);
				});
		assertThat(studentEnrollmentRepository.findByStudentId(student.getId())).hasSize(2);
	}

	@Test
	void promoteRejectsAnExplicitRollNumberAlreadyUsedInTheSection() {
		PromotionWorld w = promotionWorld("promote-roll-collision-school");
		Student first = admitAndEnroll("ADM-P-13", w.ay1(), w.from5A(), 1);
		Student second = admitAndEnroll("ADM-P-14", w.ay1(), w.from5A(), 2);
		studentService.promote(first, w.ay2(), w.to7A(), 4, LocalDate.of(2026, 4, 30), false);

		assertThatThrownBy(() -> studentService.promote(second, w.ay2(), w.to7A(), 4, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Roll number 4 is already taken");
		assertStillEnrolledIn(second, w.from5A(), 2);
	}

	@Test
	void promoteAutoAssignsMaxPlusOneEvenWhenAnExplicitNumberIsLowerOrFreesAGap() {
		PromotionWorld w = promotionWorld("promote-roll-max-school");
		Student first = admitAndEnroll("ADM-P-15", w.ay1(), w.from5A(), 1);
		Student second = admitAndEnroll("ADM-P-16", w.ay1(), w.from5A(), 2);
		Student third = admitAndEnroll("ADM-P-17", w.ay1(), w.from5A(), 3);

		assertThat(studentService.promote(first, w.ay2(), w.to7A(), 2, LocalDate.of(2026, 4, 30), false).getRollNumber()).isEqualTo(2);
		// 1 is free but auto-assign never back-fills: it is max + 1.
		assertThat(studentService.promote(second, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false).getRollNumber()).isEqualTo(3);
		assertThat(studentService.promote(third, w.ay2(), w.to7A(), 1, LocalDate.of(2026, 4, 30), false).getRollNumber()).isEqualTo(1);
	}

	@Test
	void promoteRejectsAnEarlierAcademicYear() {
		PromotionWorld w = promotionWorld("promote-earlier-year-school");
		// A year that starts before the student's current (2025-2026) year, with a grade-7 class in it.
		AcademicYear earlier = academicYearRepository.save(new AcademicYear("2024-2025", LocalDate.of(2024, 6, 1), LocalDate.of(2025, 4, 30)));
		Campus campus = campusRepository.findAll().get(0);
		GradeLevel grade7 = w.to7A().getSchoolClass().getGradeLevel();
		Section earlier7A = sectionRepository.save(new Section(schoolClassRepository.save(new SchoolClass(earlier, campus, grade7, null)), "A", 40, null));
		Student student = admitAndEnroll("ADM-P-18", w.ay1(), w.from5A(), 1);

		assertThatThrownBy(() -> studentService.promote(student, earlier, earlier7A, null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("earlier year");
		assertStillEnrolledIn(student, w.from5A());
	}

	@Test
	void promoteSkipsAnInactiveGradeWhenFindingTheNextOne() {
		PromotionWorld w = promotionWorld("promote-inactive-grade-school");
		GradeLevel grade7 = w.to7A().getSchoolClass().getGradeLevel();
		grade7.changeStatus(GradeLevelStatus.INACTIVE);
		gradeLevelRepository.save(grade7);
		Student student = admitAndEnroll("ADM-P-19", w.ay1(), w.from5A(), 1);

		// Grade 7 is inactive, so the next active grade after 5 is Grade 8.
		assertThatThrownBy(() -> studentService.promote(student, w.ay2(), w.to7A(), null, LocalDate.of(2026, 4, 30), false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be promoted to Grade 8, not Grade 7");
		assertStillEnrolledIn(student, w.from5A());
		assertThat(studentService.promote(student, w.ay2(), w.to8A(), null, LocalDate.of(2026, 4, 30), false).getSection().getId())
				.isEqualTo(w.to8A().getId());
	}

	@Test
	void theNextGradeAndRollNumberQueriesDoNotLeakAcrossTenants() {
		PromotionWorld w = promotionWorld("promote-tenant-a-school");
		Student student = admitAndEnroll("ADM-P-20", w.ay1(), w.from5A(), 3);
		Long sectionId = w.from5A().getId();
		assertThat(gradeLevelRepository.findFirstByStatusAndSequenceOrderGreaterThanOrderBySequenceOrderAsc(GradeLevelStatus.ACTIVE, 5)).isPresent();
		assertThat(studentEnrollmentRepository.existsBySectionIdAndRollNumber(sectionId, 3)).isTrue();

		Organisation other = organisationRepository.save(new Organisation("Other School", "Other School Trust", "promote-tenant-b-school"));
		TenantContext.set(other.getId(), null);

		assertThat(gradeLevelRepository.findFirstByStatusAndSequenceOrderGreaterThanOrderBySequenceOrderAsc(GradeLevelStatus.ACTIVE, 5)).isEmpty();
		assertThat(studentEnrollmentRepository.existsBySectionIdAndRollNumber(sectionId, 3)).isFalse();
		assertThat(studentEnrollmentRepository.findMaxRollNumber(sectionId, w.ay1().getId())).isNull();
		assertThat(studentEnrollmentRepository.findByStudentIdAndAcademicYearId(student.getId(), w.ay1().getId())).isEmpty();
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
				person, "ADM-002", LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
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
