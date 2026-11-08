package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamType;
import com.operion.examination.ReportCard;
import com.operion.examination.ReportCardRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.ClosedAcademicYearException;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.StudentPromotionPreviewService.ClassMapping;
import com.operion.student.StudentPromotionPreviewService.ClassPreview;
import com.operion.student.StudentPromotionPreviewService.Proposal;
import com.operion.student.StudentPromotionPreviewService.Request;
import com.operion.student.StudentPromotionPreviewService.StudentPreview;
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
class StudentPromotionPreviewTest {

	private static final LocalDate EFFECTIVE = LocalDate.of(2026, 4, 30);

	private StudentService studentService;
	private StudentPromotionPreviewService previewService;

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
	private ExamRepository examRepository;
	@Autowired
	private ReportCardRepository reportCardRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		studentService = TransactionalServiceProxy.of(new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator,
				new AuditLogService(auditLogRepository, new ObjectMapper()),
				new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper())), transactionManager);
		previewService = new StudentPromotionPreviewService(academicYearRepository, schoolClassRepository, sectionRepository,
				gradeLevelRepository, studentEnrollmentRepository, reportCardRepository);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** One organisation with a source and a target year, Grades 5 and 6 (6 is the last) and a source-year Grade 5 class with sections A and B. */
	private final class School {
		final AcademicYear source;
		final AcademicYear target;
		final Campus campus;
		final GradeLevel grade5;
		final GradeLevel grade6;
		final SchoolClass source5;
		final Section source5A;
		final Section source5B;
		final Exam finalExam;
		int admissions;

		School(String slug) {
			Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
			TenantContext.set(organisation.getId(), null);
			source = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
			target = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
			campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
			grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
			grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
			source5 = schoolClassRepository.save(new SchoolClass(source, campus, grade5, null));
			source5A = sectionRepository.save(new Section(source5, "A", 40, null));
			source5B = sectionRepository.save(new Section(source5, "B", 40, null));
			finalExam = examRepository.save(new Exam(source, "Annual", ExamType.FINAL));
		}

		SchoolClass targetClass(GradeLevel grade) {
			return schoolClassRepository.save(new SchoolClass(target, campus, grade, null));
		}

		Section section(SchoolClass schoolClass, String name, Integer capacity) {
			return sectionRepository.save(new Section(schoolClass, name, capacity, null));
		}

		Student enrolled(String firstName, Section section) {
			return admitAndEnroll(firstName, "ADM-" + System.nanoTime() + "-" + admissions++, source, section);
		}

		void card(Exam exam, Student student, boolean passed) {
			reportCardRepository.save(new ReportCard(exam, currentEnrollment(student), 70.0, 100.0, 70.0, passed ? "B" : "F", passed));
		}

		Request request() {
			return new Request(source.getId(), target.getId(), EFFECTIVE, null);
		}
	}

	@Test
	void aPassedPublishedFinalResultIsProposedForPromotionIntoTheSameNamedSection() {
		School school = new School("preview-pass-school");
		SchoolClass target6 = school.targetClass(school.grade6);
		Section target6A = school.section(target6, "A", 40);
		school.section(target6, "B", 40);
		Student student = school.enrolled("Meera", school.source5A);
		school.card(school.finalExam, student, true);

		List<ClassPreview> preview = previewService.preview(school.request());

		assertThat(preview).singleElement().satisfies(c -> {
			assertThat(c.sourceSchoolClassId()).isEqualTo(school.source5.getId());
			assertThat(c.label()).isEqualTo("Grade 5");
			assertThat(c.proposedTargetSchoolClassId()).isEqualTo(target6.getId());
			assertThat(c.proposedAction()).isEqualTo(StudentPromotionService.Action.PROMOTE);
			assertThat(c.targetSections()).extracting(StudentPromotionPreviewService.TargetSection::name).containsExactly("A", "B");
			assertThat(c.students()).singleElement().satisfies(s -> {
				assertThat(s.studentId()).isEqualTo(student.getId());
				assertThat(s.name()).isEqualTo("Meera Test");
				assertThat(s.admissionNumber()).isEqualTo(student.getAdmissionNumber());
				assertThat(s.currentSectionId()).isEqualTo(school.source5A.getId());
				assertThat(s.proposedAction()).isEqualTo(Proposal.PROMOTE);
				assertThat(s.suggestedTargetSectionId()).isEqualTo(target6A.getId());
			});
		});
	}

	@Test
	void aFailedFinalResultNeedsADecisionAndIsNeverAutoRepeated() {
		School school = new School("preview-fail-school");
		school.section(school.targetClass(school.grade6), "A", 40);
		Student student = school.enrolled("Rohan", school.source5A);
		school.card(school.finalExam, student, false);

		StudentPreview result = onlyStudent(previewService.preview(school.request()));

		assertThat(result.studentId()).isEqualTo(student.getId());
		assertThat(result.proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
		assertThat(result.reason()).isEqualTo("failed final exam");
	}

	@Test
	void aStudentWithoutAPublishedFinalResultNeedsADecision() {
		School school = new School("preview-no-card-school");
		school.section(school.targetClass(school.grade6), "A", 40);
		school.enrolled("Kiran", school.source5A);

		StudentPreview result = onlyStudent(previewService.preview(school.request()));

		assertThat(result.proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
		assertThat(result.reason()).isEqualTo("no published final result");
	}

	@Test
	void supersededAndNonFinalReportCardsAreIgnoredAndTheLatestPublishedFinalWins() {
		School school = new School("preview-unpublished-school");
		school.section(school.targetClass(school.grade6), "A", 40);
		Exam midTerm = examRepository.save(new Exam(school.source, "Mid term", ExamType.MID_TERM));
		Exam otherYearFinal = examRepository.save(new Exam(school.target, "Next year final", ExamType.FINAL));
		Student supersededOnly = school.enrolled("Asha", school.source5A);
		Student midTermOnly = school.enrolled("Bala", school.source5A);
		Student otherYearOnly = school.enrolled("Chitra", school.source5A);
		Student republished = school.enrolled("Dev", school.source5A);

		ReportCard superseded = new ReportCard(school.finalExam, currentEnrollment(supersededOnly), 90.0, 100.0, 90.0, "A", true);
		superseded.supersede();
		reportCardRepository.save(superseded);
		school.card(midTerm, midTermOnly, true);
		school.card(otherYearFinal, otherYearOnly, true);
		ReportCard oldFail = new ReportCard(school.finalExam, currentEnrollment(republished), 10.0, 100.0, 10.0, "F", false);
		oldFail.supersede();
		reportCardRepository.save(oldFail);
		school.card(school.finalExam, republished, true);

		List<StudentPreview> students = previewService.preview(school.request()).get(0).students();

		assertThat(byStudent(students, supersededOnly).proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
		assertThat(byStudent(students, supersededOnly).reason()).isEqualTo("no published final result");
		assertThat(byStudent(students, midTermOnly).proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
		assertThat(byStudent(students, otherYearOnly).proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
		assertThat(byStudent(students, republished).proposedAction()).isEqualTo(Proposal.PROMOTE);
	}

	@Test
	void theTopGradeGraduatesWithoutNeedingAFinalResult() {
		School school = new School("preview-graduate-school");
		SchoolClass source6 = schoolClassRepository.save(new SchoolClass(school.source, school.campus, school.grade6, "Grade 6 Blue"));
		Section source6A = school.section(source6, "A", 40);
		Student student = school.enrolled("Isha", source6A);

		ClassPreview grade6 = previewService.preview(school.request()).stream()
				.filter(c -> c.sourceSchoolClassId().equals(source6.getId())).findFirst().orElseThrow();

		assertThat(grade6.label()).isEqualTo("Grade 6 Blue");
		assertThat(grade6.proposedAction()).isEqualTo(StudentPromotionService.Action.GRADUATE);
		assertThat(grade6.proposedTargetSchoolClassId()).isNull();
		assertThat(grade6.students()).singleElement().satisfies(s -> {
			assertThat(s.studentId()).isEqualTo(student.getId());
			assertThat(s.proposedAction()).isEqualTo(Proposal.GRADUATE);
			assertThat(s.reason()).isEqualTo("last grade");
			assertThat(s.suggestedTargetSectionId()).isNull();
		});
	}

	@Test
	void theLastClassTheTargetYearHasGraduatesInsteadOfPointingAtAWrongClass() {
		School school = new School("preview-last-class-school");
		school.targetClass(school.grade5); // nothing above Grade 5 exists in the target year, so Grade 5 is its last class
		Student student = school.enrolled("Farah", school.source5A);

		ClassPreview preview = previewService.preview(school.request()).get(0);

		assertThat(preview.proposedAction()).isEqualTo(StudentPromotionService.Action.GRADUATE);
		assertThat(preview.proposedTargetSchoolClassId()).isNull();
		assertThat(preview.reason()).isEqualTo("last class in target year");
		assertThat(preview.students()).singleElement().satisfies(s -> {
			assertThat(s.studentId()).isEqualTo(student.getId());
			assertThat(s.proposedAction()).isEqualTo(Proposal.GRADUATE);
			assertThat(s.reason()).isEqualTo("last class in target year");
			assertThat(s.suggestedTargetSectionId()).isNull();
		});
	}

	@Test
	void aTargetYearWithNoClassesYetNeedsADecisionInsteadOfGraduatingEveryone() {
		School school = new School("preview-empty-target-school");
		school.enrolled("Farah", school.source5A);

		ClassPreview preview = previewService.preview(school.request()).get(0);

		assertThat(preview.proposedAction()).isEqualTo(StudentPromotionService.Action.PROMOTE);
		assertThat(preview.proposedTargetSchoolClassId()).isNull();
		assertThat(preview.reason()).isEqualTo("no Grade 6 class in target year");
		assertThat(preview.targetSections()).isEmpty();
		assertThat(preview.students()).singleElement().satisfies(s -> {
			assertThat(s.proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
			assertThat(s.reason()).isEqualTo("no Grade 6 class in target year");
			assertThat(s.suggestedTargetSectionId()).isNull();
		});
	}

	@Test
	void theLastClassIsJudgedPerCampusNotAcrossTheWholeTargetYear() {
		School school = new School("preview-campus-last-class-school");
		Campus other = campusRepository.save(new Campus("Other Campus", "OTHER"));
		// Main campus has no Grade 6 class; the other campus does. Main's Grade 5 must still graduate.
		school.targetClass(school.grade5);
		schoolClassRepository.save(new SchoolClass(school.target, other, school.grade6, null));
		school.enrolled("Farah", school.source5A);

		ClassPreview preview = previewService.preview(school.request()).get(0);

		assertThat(preview.proposedAction()).isEqualTo(StudentPromotionService.Action.GRADUATE);
		assertThat(preview.reason()).isEqualTo("last class in target year");
	}

	@Test
	void theNextGradeIsTheSmallestSequenceOrderAboveEvenWithGaps() {
		School school = new School("preview-gap-school");
		GradeLevel grade9 = gradeLevelRepository.save(new GradeLevel("Grade 9", 9, null));
		school.targetClass(grade9); // grade 6 is the next grade after 5, grade 9 is not
		school.enrolled("Gita", school.source5A);

		ClassPreview preview = previewService.preview(school.request()).get(0);

		assertThat(preview.proposedTargetSchoolClassId()).isNull();
		assertThat(preview.reason()).isEqualTo("no Grade 6 class in target year");
	}

	@Test
	void aStudentAlreadyEnrolledInTheTargetYearIsAlreadyDone() {
		School school = new School("preview-already-school");
		SchoolClass target6 = school.targetClass(school.grade6);
		Section target6A = school.section(target6, "A", 40);
		Student student = school.enrolled("Hari", school.source5A);
		school.card(school.finalExam, student, true);
		studentService.promote(student, school.target, target6A, null, EFFECTIVE, false);
		Student other = school.enrolled("Ila", school.source5A);
		school.card(school.finalExam, other, true);

		// The promoted student's source-year enrolment is closed, so only the one still to move is listed...
		assertThat(previewService.preview(school.request()).get(0).students()).extracting(StudentPreview::studentId).containsExactly(other.getId());

		// ...but a student who still holds a current source-year enrolment and already has a target-year row is ALREADY_DONE.
		Student alreadyPlaced = school.enrolled("Jai", school.source5B);
		studentEnrollmentRepository.save(new StudentEnrollment(alreadyPlaced, school.target, target6A, 9, EFFECTIVE));

		StudentPreview result = byStudent(previewService.preview(school.request()).get(0).students(), alreadyPlaced);
		assertThat(result.proposedAction()).isEqualTo(Proposal.ALREADY_DONE);
		assertThat(result.suggestedTargetSectionId()).isNull();
	}

	@Test
	void aClassMapEntryOverridesTheDefaultAndOtherClassesKeepTheirDefault() {
		School school = new School("preview-override-school");
		SchoolClass default6 = school.targetClass(school.grade6);
		school.section(default6, "A", 40);
		Campus annex = campusRepository.save(new Campus("Annex", "ANX"));
		SchoolClass annex6 = schoolClassRepository.save(new SchoolClass(school.target, annex, school.grade6, "Annex Grade 6"));
		Section annex6B = school.section(annex6, "B", 40);
		SchoolClass source6 = schoolClassRepository.save(new SchoolClass(school.source, school.campus, school.grade6, null));
		Student promoted = school.enrolled("Kavya", school.source5B);
		school.card(school.finalExam, promoted, true);
		Student graduating = school.enrolled("Lata", school.section(source6, "A", 40));

		List<ClassPreview> preview = previewService.preview(new Request(school.source.getId(), school.target.getId(), EFFECTIVE,
				List.of(new ClassMapping(school.source5.getId(), annex6.getId()), new ClassMapping(source6.getId(), null))));

		ClassPreview grade5 = preview.stream().filter(c -> c.sourceSchoolClassId().equals(school.source5.getId())).findFirst().orElseThrow();
		assertThat(grade5.proposedTargetSchoolClassId()).isEqualTo(annex6.getId());
		assertThat(grade5.targetSections()).extracting(StudentPromotionPreviewService.TargetSection::id).containsExactly(annex6B.getId());
		assertThat(byStudent(grade5.students(), promoted).suggestedTargetSectionId()).isEqualTo(annex6B.getId());
		ClassPreview grade6 = preview.stream().filter(c -> c.sourceSchoolClassId().equals(source6.getId())).findFirst().orElseThrow();
		assertThat(grade6.proposedAction()).isEqualTo(StudentPromotionService.Action.GRADUATE);
		assertThat(byStudent(grade6.students(), graduating).proposedAction()).isEqualTo(Proposal.GRADUATE);

		// Without the override the default (same campus, next grade) applies.
		List<ClassPreview> defaults = previewService.preview(school.request());
		assertThat(defaults.stream().filter(c -> c.sourceSchoolClassId().equals(school.source5.getId())).findFirst().orElseThrow()
				.proposedTargetSchoolClassId()).isEqualTo(default6.getId());
	}

	@Test
	void reportsRemainingCapacityPerTargetSection() {
		School school = new School("preview-capacity-school");
		SchoolClass target6 = school.targetClass(school.grade6);
		Section small = school.section(target6, "A", 2);
		Section unlimited = school.section(target6, "B", null);
		Section full = school.section(target6, "C", 1);
		Student placed = school.enrolled("Mira", school.source5A);
		studentService.promote(placed, school.target, small, null, EFFECTIVE, false);
		Student placedInFull = school.enrolled("Nina", school.source5A);
		studentService.promote(placedInFull, school.target, full, null, EFFECTIVE, false);
		school.enrolled("Omar", school.source5A);

		ClassPreview preview = previewService.preview(school.request()).get(0);

		assertThat(preview.targetSections()).extracting(StudentPromotionPreviewService.TargetSection::id, StudentPromotionPreviewService.TargetSection::remainingCapacity)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(small.getId(), 1), org.assertj.core.groups.Tuple.tuple(unlimited.getId(), null),
						org.assertj.core.groups.Tuple.tuple(full.getId(), 0));
	}

	@Test
	void exitedStudentsAreNotListed() {
		School school = new School("preview-exited-school");
		school.section(school.targetClass(school.grade6), "A", 40);
		Student stays = school.enrolled("Pia", school.source5A);
		Student leaves = school.enrolled("Quin", school.source5A);
		studentService.recordExit(leaves, StudentExitType.WITHDRAWAL, EFFECTIVE, "moved", null, null);

		assertThat(previewService.preview(school.request()).get(0).students()).extracting(StudentPreview::studentId).containsExactly(stays.getId());
	}

	@Test
	void aClosedOrSameTargetYearAndBadIdsAreRejected() {
		School school = new School("preview-validation-school");
		school.enrolled("Ravi", school.source5A);

		assertThatThrownBy(() -> previewService.preview(new Request(school.source.getId(), school.source.getId(), EFFECTIVE, null)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("must differ");

		school.target.close();
		academicYearRepository.save(school.target);
		assertThatThrownBy(() -> previewService.preview(school.request())).isInstanceOf(ClosedAcademicYearException.class);

		School other = new School("preview-validation-school-2");
		assertThatThrownBy(() -> previewService.preview(new Request(other.source.getId(), 999_999L, EFFECTIVE, null)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No academic year");
		assertThatThrownBy(() -> previewService.preview(new Request(other.source.getId(), other.target.getId(), null, null)))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> previewService.preview(new Request(other.source.getId(), other.target.getId(), EFFECTIVE,
				List.of(new ClassMapping(999_999L, null))))).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> previewService.preview(new Request(other.source.getId(), other.target.getId(), EFFECTIVE,
				List.of(new ClassMapping(other.source5.getId(), other.source5.getId()))))).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void previewWritesNothing() {
		School school = new School("preview-readonly-school");
		SchoolClass target6 = school.targetClass(school.grade6);
		school.section(target6, "A", 40);
		Student passed = school.enrolled("Sara", school.source5A);
		school.card(school.finalExam, passed, true);
		school.enrolled("Tej", school.source5B);

		long enrollments = studentEnrollmentRepository.count();
		long audits = auditLogRepository.count();
		long students = studentRepository.count();
		long reportCards = reportCardRepository.count();
		long sections = sectionRepository.count();
		List<StudentEnrollment> before = studentEnrollmentRepository.findByAcademicYearId(school.source.getId());

		previewService.preview(school.request());
		previewService.preview(school.request());

		assertThat(studentEnrollmentRepository.count()).isEqualTo(enrollments);
		assertThat(auditLogRepository.count()).isEqualTo(audits);
		assertThat(studentRepository.count()).isEqualTo(students);
		assertThat(reportCardRepository.count()).isEqualTo(reportCards);
		assertThat(sectionRepository.count()).isEqualTo(sections);
		assertThat(studentEnrollmentRepository.findByAcademicYearId(school.target.getId())).isEmpty();
		assertThat(studentEnrollmentRepository.findByAcademicYearId(school.source.getId()))
				.extracting(StudentEnrollment::getId, StudentEnrollment::isCurrent, StudentEnrollment::getEnrollmentStatus)
				.containsExactlyInAnyOrderElementsOf(before.stream()
						.map(e -> org.assertj.core.groups.Tuple.tuple(e.getId(), e.isCurrent(), e.getEnrollmentStatus())).toList());
	}

	@Test
	void anotherOrganisationsStudentsAndClassesNeverAppear() {
		School mine = new School("preview-tenant-mine-school");
		mine.section(mine.targetClass(mine.grade6), "A", 40);
		Student myStudent = mine.enrolled("Uma", mine.source5A);
		long mineOrg = TenantContext.getOrganisationId();

		School theirs = new School("preview-tenant-theirs-school");
		theirs.section(theirs.targetClass(theirs.grade6), "A", 40);
		Student theirStudent = theirs.enrolled("Vik", theirs.source5A);
		theirs.card(theirs.finalExam, theirStudent, true);

		// Their own preview shows only their data.
		ClassPreview theirPreview = previewService.preview(theirs.request()).get(0);
		assertThat(theirPreview.students()).extracting(StudentPreview::studentId).containsExactly(theirStudent.getId());
		assertThat(previewService.preview(theirs.request())).hasSize(1);

		// Asked from my tenant, their years do not exist and their classes cannot be named in a class map.
		TenantContext.set(mineOrg, null);
		assertThatThrownBy(() -> previewService.preview(theirs.request())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> previewService.preview(new Request(mine.source.getId(), mine.target.getId(), EFFECTIVE,
				List.of(new ClassMapping(theirs.source5.getId(), null))))).isInstanceOf(InvalidRequestException.class);
		List<ClassPreview> myPreview = previewService.preview(mine.request());
		assertThat(myPreview).hasSize(1);
		assertThat(myPreview.get(0).sourceSchoolClassId()).isEqualTo(mine.source5.getId());
		assertThat(myPreview.get(0).students()).extracting(StudentPreview::studentId).containsExactly(myStudent.getId());
		assertThat(myPreview.get(0).students().get(0).proposedAction()).isEqualTo(Proposal.NEEDS_DECISION);
	}

	private StudentPreview onlyStudent(List<ClassPreview> preview) {
		assertThat(preview).hasSize(1);
		assertThat(preview.get(0).students()).hasSize(1);
		return preview.get(0).students().get(0);
	}

	private static StudentPreview byStudent(List<StudentPreview> students, Student student) {
		return students.stream().filter(s -> s.studentId().equals(student.getId())).findFirst().orElseThrow();
	}

	private StudentEnrollment currentEnrollment(Student student) {
		return studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).orElseThrow();
	}

	private Student admitAndEnroll(String firstName, String admissionNumber, AcademicYear year, Section section) {
		Person person = personRepository.save(new Person(firstName, "Test"));
		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, null, null, null, null, null);
		studentService.enroll(student, year, section, null, LocalDate.of(2025, 6, 1));
		return student;
	}
}
