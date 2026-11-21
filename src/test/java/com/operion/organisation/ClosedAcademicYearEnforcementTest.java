package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.operion.academic.AcademicService;
import com.operion.academic.ClassSectionRowImportService;
import com.operion.academic.ClassSubject;
import com.operion.academic.ClassSubjectRepository;
import com.operion.academic.ClassSubjectStatus;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SchoolClassStatus;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SectionStatus;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.AttendanceService;
import com.operion.attendance.AttendanceService.StudentAttendanceMark;
import com.operion.attendance.AttendanceCorrectionRepository;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.attendance.StudentAttendance;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryType;
import com.operion.finance.FeeService;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.FeeService.InstallmentInput;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureInstallment;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.InvoiceStatus;
import com.operion.finance.Payment;
import com.operion.finance.PaymentMethod;
import com.operion.finance.PaymentRepository;
import com.operion.finance.StudentFeeAssignment;
import com.operion.finance.StudentFeeAssignmentRepository;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamSchedule;
import com.operion.examination.ExamScheduleRepository;
import com.operion.examination.ExamType;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.examination.ExaminationSettingsRepository;
import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleBandRepository;
import com.operion.examination.GradingScaleRepository;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRegisterRepository;
import com.operion.examination.MarksEntryRepository;
import com.operion.examination.ReportCardRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.communication.CommunicationService;
import com.operion.learning.Homework;
import com.operion.learning.HomeworkService;
import com.operion.learning.LearningActor;
import com.operion.learning.QuizService;
import com.operion.timetable.Period;
import com.operion.transport.Route;
import com.operion.transport.RouteRepository;
import com.operion.transport.RouteStop;
import com.operion.transport.RouteStopRepository;
import com.operion.transport.StudentTransportAssignment;
import com.operion.transport.StudentTransportAssignmentRepository;
import com.operion.transport.TransportAssignmentStatus;
import com.operion.transport.TransportNotificationService;
import com.operion.transport.TransportService;
import com.operion.timetable.TimetableEntryRepository;
import com.operion.timetable.TimetableService;
import com.operion.student.EnrolmentClosureCheck;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentEnrollmentStatus;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentPromotionService;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import com.operion.student.StudentStatus;
import com.operion.modules.ModuleAccessService;
import com.operion.student.AdmissionFeeGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * The owner rule end to end against real rows: once an academic year is CLOSED nothing scoped to it may
 * change through the academic-structure, student, attendance or examination services - each rejects with
 * ClosedAcademicYearException - while the same calls still work in an open year. Also covers the other half
 * of "promote first, then close": a year cannot be closed while students are still enrolled in it.
 *
 * A closed year holding live data is set up the way legacy data would have it: everything is created while
 * the year is open, then the year is closed directly (bypassing AcademicYearService's precondition).
 * Services are built by hand, like the other module tests.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class, AcademicYearService.class, EnrolmentClosureCheck.class, AdmissionFeeGate.class, ModuleAccessService.class,
		FeeService.class, TimetableService.class, HomeworkService.class, QuizService.class, TransportService.class, TransportNotificationService.class,
		CommunicationService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClosedAcademicYearEnforcementTest {

	@Autowired private AdmissionFeeGate admissionFeeGate;
	@Autowired private FeeService feeService;
	@Autowired private FeeStructureInstallmentRepository feeStructureInstallmentRepository;
	@Autowired private InvoiceRepository invoiceRepository;
	@Autowired private StudentFeeAssignmentRepository studentFeeAssignmentRepository;
	@Autowired private PaymentRepository paymentRepository;
	@Autowired private TimetableService timetableService;
	@Autowired private TimetableEntryRepository timetableEntryRepository;
	@Autowired private HomeworkService homeworkService;
	@Autowired private QuizService quizService;
	@Autowired private TransportService transportService;
	@Autowired private RouteRepository routeRepository;
	@Autowired private RouteStopRepository routeStopRepository;
	@Autowired private StudentTransportAssignmentRepository studentTransportAssignmentRepository;

	private static final LocalDate DAY_ONE = LocalDate.of(2025, 7, 1);
	private static final LocalDate DAY_TWO = LocalDate.of(2025, 7, 2);

	private AcademicService academicService;
	private StudentService studentService;
	private StudentPromotionService promotionService;
	private AttendanceService attendanceService;
	private ExaminationService examinationService;
	private ClassSectionRowImportService classSectionImport;

	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private AcademicYearService academicYearService;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SubjectRepository subjectRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private ClassSubjectRepository classSubjectRepository;
	@Autowired private TeacherAssignmentRepository teacherAssignmentRepository;
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired private StudentDocumentRepository studentDocumentRepository;
	@Autowired private StudentExitRepository studentExitRepository;
	@Autowired private StudentIdGenerator studentIdGenerator;
	@Autowired private StudentAttendanceRepository studentAttendanceRepository;
	@Autowired private ClassAttendanceRegisterRepository classAttendanceRegisterRepository;
	@Autowired private AttendanceCorrectionRepository attendanceCorrectionRepository;
	@Autowired private StaffAttendanceRepository staffAttendanceRepository;
	@Autowired private ExamRepository examRepository;
	@Autowired private ExamScheduleRepository examScheduleRepository;
	@Autowired private GradingScaleRepository gradingScaleRepository;
	@Autowired private GradingScaleBandRepository gradingScaleBandRepository;
	@Autowired private MarksEntryRepository marksEntryRepository;
	@Autowired private MarksEntryRegisterRepository marksEntryRegisterRepository;
	@Autowired private ReportCardRepository reportCardRepository;
	@Autowired private ExaminationSettingsRepository examinationSettingsRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@BeforeEach
	void setUpServices() {
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		academicService = new AcademicService(gradeLevelRepository, subjectRepository, schoolClassRepository, sectionRepository,
				classSubjectRepository, teacherAssignmentRepository);
		// promote() row-locks the target section, which needs a transaction - proxy the hand-built service like Spring does.
		ProxyFactory studentServiceProxy = new ProxyFactory(new StudentService(studentRepository, studentEnrollmentRepository,
				studentDocumentRepository, studentExitRepository, sectionRepository, gradeLevelRepository, null, null, studentIdGenerator,
				auditLogService, new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper())));
		studentServiceProxy.setProxyTargetClass(true);
		studentServiceProxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
		studentService = (StudentService) studentServiceProxy.getProxy();
		promotionService = new StudentPromotionService(studentService, studentRepository, academicYearRepository, sectionRepository,
				studentEnrollmentRepository);
		attendanceService = new AttendanceService(studentAttendanceRepository, classAttendanceRegisterRepository,
				attendanceCorrectionRepository, staffAttendanceRepository, auditLogService);
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, auditLogService, admissionFeeGate);
		classSectionImport = new ClassSectionRowImportService(academicYearRepository, campusRepository, gradeLevelRepository,
				schoolClassRepository, sectionRepository, academicService);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	/** Everything below hangs off two years: {@code year} (2025-2026, where the live data sits) and {@code nextYear}. */
	private record Fixture(AcademicYear year, AcademicYear nextYear, Campus campus, GradeLevel grade5, SchoolClass class5,
			Section sectionA, Section sectionB, Section nextYearSectionA, Subject maths, ClassSubject classSubject,
			TeacherAssignment assignment, Person teacher, Student student, StudentEnrollment enrollment, Exam exam,
			ExamSchedule schedule, MarksEntry marks, GradingScale gradingScale) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		AcademicYear nextYear = academicYearRepository.save(new AcademicYear("2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		Subject maths = subjectRepository.save(new Subject("Mathematics", "MATH"));
		Person teacher = personRepository.save(new Person("Anil", "Rao"));

		SchoolClass class5 = academicService.createSchoolClass(year, campus, grade5, null);
		Section sectionA = academicService.createSection(class5, "A", 40, null);
		Section sectionB = academicService.createSection(class5, "B", 40, null);
		// Promotion must land in the next grade, so the target year's class is Grade 6.
		GradeLevel grade6 = gradeLevelRepository.save(new GradeLevel("Grade 6", 6, null));
		SchoolClass nextYearClass6 = academicService.createSchoolClass(nextYear, campus, grade6, null);
		Section nextYearSectionA = academicService.createSection(nextYearClass6, "A", 40, null);
		ClassSubject classSubject = academicService.assignSubjectToClass(class5, maths, true);
		TeacherAssignment assignment =
				academicService.assignTeacher(sectionA, maths, teacher, TeacherAssignmentType.SUBJECT, LocalDate.of(2025, 6, 1));

		Person person = personRepository.save(new Person("Meera", "Nair"));
		Student student = studentService.admit(person, "ADM-" + slug, LocalDate.of(2025, 5, 1), null, null, null, null, null, null,
				"Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, year, sectionA, 1, LocalDate.of(2025, 6, 1));

		Exam exam = examinationService.createExam(year, "Term 1 Unit Test", ExamType.UNIT_TEST);
		ExamSchedule schedule = examinationService.addSchedule(exam, class5, maths, LocalDate.of(2025, 8, 1), 100.0, 35.0);
		MarksEntry marks = examinationService.enterMarks(schedule, List.of(new MarkInput(enrollment, 80.0, false, null))).get(0);
		GradingScale gradingScale = examinationService.createGradingScale("Standard", true, List.of(new BandInput("A", 0.0, null)));

		attendanceService.markDailyAttendance(year, sectionA, DAY_ONE,
				List.of(new StudentAttendanceMark(enrollment, AttendanceStatus.PRESENT, false, null)));

		return new Fixture(year, nextYear, campus, grade5, class5, sectionA, sectionB, nextYearSectionA, maths, classSubject,
				assignment, teacher, student, enrollment, exam, schedule, marks, gradingScale);
	}

	/** Closes the year directly - the AcademicYearService precondition (no enrolled students) is tested separately. */
	private void closeYear(AcademicYear year) {
		year.close();
		academicYearRepository.save(year);
	}

	private static void assertClosed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(ClosedAcademicYearException.class).hasMessageContaining("is closed");
	}

	// ---- academic structure ------------------------------------------------------------------------------

	@Test
	void academicStructureWritesAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-structure");
		closeYear(fx.year());
		SchoolClass schoolClass = schoolClassRepository.findById(fx.class5().getId()).orElseThrow();
		Section section = sectionRepository.findById(fx.sectionA().getId()).orElseThrow();
		ClassSubject classSubject = classSubjectRepository.findById(fx.classSubject().getId()).orElseThrow();
		TeacherAssignment assignment = teacherAssignmentRepository.findById(fx.assignment().getId()).orElseThrow();
		Subject science = subjectRepository.save(new Subject("Science", "SCI"));

		assertClosed(() -> academicService.createSchoolClass(fx.year(), fx.campus(), fx.grade5(), "Another"));
		assertClosed(() -> academicService.createSection(schoolClass, "C", 30, null));
		assertClosed(() -> academicService.updateSection(section, "A2", 35, null));
		assertClosed(() -> academicService.changeSectionStatus(section, SectionStatus.INACTIVE));
		assertClosed(() -> academicService.assignSubjectToClass(schoolClass, science, true));
		assertClosed(() -> academicService.assignSubjectToClass(schoolClass, science, true, 5));
		assertClosed(() -> academicService.updateClassSubject(classSubject, false, 4));
		assertClosed(() -> academicService.changeClassSubjectStatus(classSubject, ClassSubjectStatus.INACTIVE));
		assertClosed(() -> academicService.updateClassSubjectMandatory(classSubject, false));
		assertClosed(() -> academicService.assignTeacher(section, fx.maths(), fx.teacher(), TeacherAssignmentType.SUBJECT, DAY_TWO));
		assertClosed(() -> academicService.endTeacherAssignment(assignment, DAY_TWO));
		assertClosed(() -> academicService.changeSchoolClassStatus(schoolClass, SchoolClassStatus.INACTIVE));
		assertClosed(() -> academicService.updateSchoolClassDisplayName(schoolClass, "Renamed"));

		// Nothing slipped through: the rows are exactly as they were.
		assertThat(sectionRepository.findBySchoolClassIdAndNameIgnoreCase(fx.class5().getId(), "C")).isEmpty();
		assertThat(sectionRepository.findById(fx.sectionA().getId()).orElseThrow().getName()).isEqualTo("A");
		assertThat(teacherAssignmentRepository.findById(fx.assignment().getId()).orElseThrow().getEndDate()).isNull();
	}

	@Test
	void classSectionImportRejectsAClosedYearEvenAsADryRun() {
		Fixture fx = setUpFixture("closed-import");
		closeYear(fx.year());
		Map<String, String> row = Map.of("academicYear", "2025-2026", "campus", "Main Campus", "gradeLevel", "Grade 5",
				"className", "Grade 5", "sectionName", "Z");

		assertClosed(() -> classSectionImport.importRow(2, row, true));
		assertClosed(() -> classSectionImport.importRow(2, row, false));
		assertThat(sectionRepository.findBySchoolClassIdAndNameIgnoreCase(fx.class5().getId(), "Z")).isEmpty();
	}

	@Test
	void academicStructureWritesStillWorkInAnOpenYear() {
		Fixture fx = setUpFixture("open-structure");
		Section section = sectionRepository.findById(fx.sectionA().getId()).orElseThrow();

		academicService.updateSection(section, "A2", 35, null);
		academicService.changeSectionStatus(section, SectionStatus.ACTIVE);
		academicService.updateClassSubject(fx.classSubject(), false, 4);
		academicService.endTeacherAssignment(fx.assignment(), DAY_TWO);
		academicService.updateSchoolClassDisplayName(fx.class5(), "Renamed");
		Section created = academicService.createSection(fx.class5(), "C", 30, null);
		Map<String, String> row = Map.of("academicYear", "2025-2026", "campus", "Main Campus", "gradeLevel", "Grade 5",
				"className", "Grade 5", "sectionName", "Z");
		ImportRowResult imported = classSectionImport.importRow(2, row, false);

		assertThat(created.getId()).isNotNull();
		assertThat(sectionRepository.findById(fx.sectionA().getId()).orElseThrow().getName()).isEqualTo("A2");
		assertThat(imported.status()).isEqualTo(ImportRowStatus.IMPORTED);
	}

	// ---- student enrolment, promotion, exit ---------------------------------------------------------------

	@Test
	void promotingOutOfAClosedSourceYearIsRejectedAndLeavesTheEnrolmentUntouched() {
		Fixture fx = setUpFixture("closed-promote-source");
		closeYear(fx.year());

		assertClosed(() -> studentService.promote(fx.student(), fx.nextYear(), fx.nextYearSectionA(), 1, LocalDate.of(2026, 6, 1), false));

		assertCurrentEnrolment(fx, fx.year(), StudentEnrollmentStatus.ENROLLED);
		assertThat(studentEnrollmentRepository.findByStudentId(fx.student().getId())).hasSize(1);
	}

	@Test
	void promotingIntoAClosedTargetYearIsRejectedAndLeavesTheEnrolmentUntouched() {
		Fixture fx = setUpFixture("closed-promote-target");
		closeYear(fx.nextYear());

		assertClosed(() -> studentService.promote(fx.student(), fx.nextYear(), fx.nextYearSectionA(), 1, LocalDate.of(2026, 6, 1), false));

		assertCurrentEnrolment(fx, fx.year(), StudentEnrollmentStatus.ENROLLED);
		assertThat(studentEnrollmentRepository.findByStudentId(fx.student().getId())).hasSize(1);
	}

	@Test
	void promotionStillWorksBetweenOpenYears() {
		Fixture fx = setUpFixture("open-promote");

		StudentEnrollment promoted =
				studentService.promote(fx.student(), fx.nextYear(), fx.nextYearSectionA(), 1, LocalDate.of(2026, 6, 1), false);

		assertThat(promoted.getAcademicYear().getId()).isEqualTo(fx.nextYear().getId());
		assertCurrentEnrolment(fx, fx.nextYear(), StudentEnrollmentStatus.ENROLLED);
	}

	@Test
	void reassigningASectionInAClosedYearIsRejected() {
		Fixture fx = setUpFixture("closed-reassign");
		closeYear(fx.year());
		Section sectionB = sectionRepository.findById(fx.sectionB().getId()).orElseThrow();

		assertClosed(() -> studentService.reassignSection(fx.student(), sectionB));

		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(fx.student().getId()).orElseThrow().getSection().getId())
				.isEqualTo(fx.sectionA().getId());
	}

	@Test
	void reassigningASectionStillWorksInAnOpenYear() {
		Fixture fx = setUpFixture("open-reassign");

		studentService.reassignSection(fx.student(), fx.sectionB());

		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(fx.student().getId()).orElseThrow().getSection().getId())
				.isEqualTo(fx.sectionB().getId());
	}

	@Test
	void recordingAnExitFromAClosedYearIsRejectedAndChangesNothing() {
		Fixture fx = setUpFixture("closed-exit");
		closeYear(fx.year());

		assertClosed(() -> studentService.recordExit(fx.student(), StudentExitType.TRANSFER, LocalDate.of(2026, 3, 1), "moved", null, null));

		assertCurrentEnrolment(fx, fx.year(), StudentEnrollmentStatus.ENROLLED);
		assertThat(studentRepository.findById(fx.student().getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.ACTIVE);
		assertThat(studentExitRepository.findAll()).isEmpty();
	}

	@Test
	void recordingAnExitStillWorksInAnOpenYear() {
		Fixture fx = setUpFixture("open-exit");

		studentService.recordExit(fx.student(), StudentExitType.TRANSFER, LocalDate.of(2026, 3, 1), "moved", null, null);

		assertThat(studentRepository.findById(fx.student().getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.TRANSFERRED_OUT);
		assertThat(studentEnrollmentRepository.findByStudentIdAndCurrentTrue(fx.student().getId())).isEmpty();
	}

	@Test
	void bulkPromotionIntoAClosedTargetYearIsRejectedUpFrontBeforeAnyStudentIsTouched() {
		Fixture fx = setUpFixture("closed-bulk-target");
		closeYear(fx.nextYear());

		assertClosed(() -> promotionService.apply(fx.nextYear().getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(fx.student().getId(), StudentPromotionService.Action.PROMOTE, fx.nextYearSectionA().getId(), 1)),
				null));

		assertCurrentEnrolment(fx, fx.year(), StudentEnrollmentStatus.ENROLLED);
	}

	@Test
	void bulkPromotionOrGraduationOutOfAClosedSourceYearIsRejectedUpFrontBeforeAnyStudentIsTouched() {
		Fixture fx = setUpFixture("closed-bulk-source");
		closeYear(fx.year());

		assertClosed(() -> promotionService.apply(fx.nextYear().getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(fx.student().getId(), StudentPromotionService.Action.PROMOTE, fx.nextYearSectionA().getId(), 1)),
				null));
		assertClosed(() -> promotionService.apply(null, LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(fx.student().getId(), StudentPromotionService.Action.GRADUATE, null, null)), null));

		assertCurrentEnrolment(fx, fx.year(), StudentEnrollmentStatus.ENROLLED);
		assertThat(studentRepository.findById(fx.student().getId()).orElseThrow().getStatus()).isEqualTo(StudentStatus.ACTIVE);
	}

	@Test
	void bulkPromotionStillWorksBetweenOpenYears() {
		Fixture fx = setUpFixture("open-bulk");

		List<StudentPromotionService.Outcome> outcomes = promotionService.apply(fx.nextYear().getId(), LocalDate.of(2026, 6, 1), List.of(
				new StudentPromotionService.Decision(fx.student().getId(), StudentPromotionService.Action.PROMOTE, fx.nextYearSectionA().getId(), 1)),
				null);

		assertThat(outcomes).extracting(StudentPromotionService.Outcome::succeeded).containsExactly(true);
		assertCurrentEnrolment(fx, fx.nextYear(), StudentEnrollmentStatus.ENROLLED);
	}

	private void assertCurrentEnrolment(Fixture fx, AcademicYear expectedYear, StudentEnrollmentStatus expectedStatus) {
		StudentEnrollment current = studentEnrollmentRepository.findByStudentIdAndCurrentTrue(fx.student().getId()).orElseThrow();
		assertThat(current.getAcademicYear().getId()).isEqualTo(expectedYear.getId());
		assertThat(current.getEnrollmentStatus()).isEqualTo(expectedStatus);
	}

	// ---- attendance --------------------------------------------------------------------------------------

	@Test
	void attendanceWritesAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-attendance");
		closeYear(fx.year());
		ClassAttendanceRegister register =
				classAttendanceRegisterRepository.findBySectionIdAndAttendanceDate(fx.sectionA().getId(), DAY_ONE).orElseThrow();
		StudentAttendance attendance =
				studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDate(fx.enrollment().getId(), DAY_ONE).orElseThrow();
		Section section = sectionRepository.findById(fx.sectionA().getId()).orElseThrow();

		assertClosed(() -> attendanceService.markDailyAttendance(fx.year(), section, DAY_TWO,
				List.of(new StudentAttendanceMark(fx.enrollment(), AttendanceStatus.PRESENT, false, null))));
		assertClosed(() -> attendanceService.submitRegister(register));
		assertClosed(() -> attendanceService.lockRegister(register));
		assertClosed(() -> attendanceService.unlockRegister(register));
		assertClosed(() -> attendanceService.correct(attendance, AttendanceStatus.ABSENT, "wrong"));

		assertThat(classAttendanceRegisterRepository.findBySectionIdAndAttendanceDate(fx.sectionA().getId(), DAY_TWO)).isEmpty();
		assertThat(studentAttendanceRepository.findById(attendance.getId()).orElseThrow().getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
		assertThat(attendanceCorrectionRepository.findAll()).isEmpty();
	}

	@Test
	void attendanceStillWorksInAnOpenYear() {
		Fixture fx = setUpFixture("open-attendance");
		ClassAttendanceRegister register =
				classAttendanceRegisterRepository.findBySectionIdAndAttendanceDate(fx.sectionA().getId(), DAY_ONE).orElseThrow();
		StudentAttendance attendance =
				studentAttendanceRepository.findByStudentEnrollmentIdAndAttendanceDate(fx.enrollment().getId(), DAY_ONE).orElseThrow();

		attendanceService.markDailyAttendance(fx.year(), fx.sectionA(), DAY_TWO,
				List.of(new StudentAttendanceMark(fx.enrollment(), AttendanceStatus.PRESENT, false, null)));
		attendanceService.correct(attendance, AttendanceStatus.ABSENT, "wrong");
		attendanceService.submitRegister(register);
		attendanceService.lockRegister(register);
		attendanceService.unlockRegister(register);

		assertThat(studentAttendanceRepository.findById(attendance.getId()).orElseThrow().getAttendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
	}

	// ---- examination -------------------------------------------------------------------------------------

	@Test
	void examinationWritesAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-exams");
		Subject science = subjectRepository.save(new Subject("Science", "SCI"));
		closeYear(fx.year());
		Exam exam = examRepository.findById(fx.exam().getId()).orElseThrow();
		ExamSchedule schedule = examScheduleRepository.findById(fx.schedule().getId()).orElseThrow();
		MarksEntry marks = marksEntryRepository.findById(fx.marks().getId()).orElseThrow();

		assertClosed(() -> examinationService.createExam(fx.year(), "Term 2", ExamType.MID_TERM));
		assertClosed(() -> examinationService.addSchedule(exam, fx.class5(), science, LocalDate.of(2025, 8, 2), 100.0, 35.0));
		assertClosed(() -> examinationService.addSchedule(exam, fx.class5(), fx.sectionA(), science, LocalDate.of(2025, 8, 2), 100.0, 35.0));
		assertClosed(() -> examinationService.enterMarks(schedule, List.of(new MarkInput(fx.enrollment(), 50.0, false, null))));
		assertClosed(() -> examinationService.submitMarksRegister(schedule));
		assertClosed(() -> examinationService.approveMarksRegister(schedule));
		assertClosed(() -> examinationService.correctMarks(marks, 90.0, false, null));
		assertClosed(() -> examinationService.correctMarksAfterPublish(marks, 90.0, false, null));
		assertClosed(() -> examinationService.publishReportCard(exam, fx.enrollment(), fx.gradingScale()));

		assertThat(marksEntryRepository.findById(fx.marks().getId()).orElseThrow().getMarksObtained()).isEqualTo(80.0);
		assertThat(examScheduleRepository.findByExamIdAndSchoolClassIdAndSubjectId(exam.getId(), fx.class5().getId(), science.getId())).isEmpty();
		assertThat(reportCardRepository.findAll()).isEmpty();
	}

	@Test
	void examinationWritesStillWorkInAnOpenYear() {
		Fixture fx = setUpFixture("open-exams");

		examinationService.correctMarks(fx.marks(), 90.0, false, "recheck");
		examinationService.submitMarksRegister(fx.schedule());
		examinationService.approveMarksRegister(fx.schedule());
		examinationService.publishReportCard(fx.exam(), fx.enrollment(), fx.gradingScale());
		examinationService.createExam(fx.year(), "Term 2", ExamType.MID_TERM);

		assertThat(marksEntryRepository.findById(fx.marks().getId()).orElseThrow().getMarksObtained()).isEqualTo(90.0);
		assertThat(reportCardRepository.findAll()).hasSize(1);
	}

	// ---- finance -----------------------------------------------------------------------------------------

	private record FeeSetup(FeeStructureGroup group, FeeCategory tuition, FeeCategory lab, FeeStructure structure,
			FeeStructure secondStructure, StudentFeeAssignment assignment, FeeStructureInstallment installment, Invoice invoice) {
	}

	/** Fee structures, an assignment and one invoice, all created while the year is open. */
	private FeeSetup setUpFees(Fixture fx) {
		FeeStructureGroup group = feeService.createFeeStructureGroup("Grade 5 Fees", fx.year(), fx.class5());
		FeeCategory tuition = feeService.createCategory("TUITION", "Tuition Fee", null, FeeCategoryType.GENERAL);
		FeeCategory lab = feeService.createCategory("LAB", "Lab Fee", null, FeeCategoryType.GENERAL);
		List<InstallmentInput> installments = List.of(new InstallmentInput(1, LocalDate.of(2025, 6, 15), new BigDecimal("10000.00")));
		FeeStructure structure = feeService.createFeeStructure(group, tuition, new BigDecimal("10000.00"), installments);
		FeeStructure second = feeService.createFeeStructure(group, lab, new BigDecimal("10000.00"), installments);
		StudentFeeAssignment assignment = feeService.assignFee(fx.enrollment(), structure, null, null, null);
		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.findByFeeStructureIdOrderByInstallmentNumber(structure.getId()).get(0);
		Invoice invoice = feeService.generateInvoice(assignment, installment);
		return new FeeSetup(group, tuition, lab, structure, second, assignment, installment, invoice);
	}

	@Test
	void feeSetupAssignmentRevisionAndInvoicingAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-fees");
		FeeSetup fees = setUpFees(fx);
		Person other = personRepository.save(new Person("Rohan", "Das"));
		Student second = studentService.admit(other, "ADM-closed-fees-2", LocalDate.of(2025, 5, 1), null, null, null, null, null, null,
				"Indian", null, null, null, null);
		StudentEnrollment secondEnrollment = studentService.enroll(second, fx.year(), fx.sectionA(), 2, LocalDate.of(2025, 6, 1));
		closeYear(fx.year());
		List<InstallmentInput> installments = List.of(new InstallmentInput(1, LocalDate.of(2025, 7, 15), new BigDecimal("500.00")));

		assertClosed(() -> feeService.createFeeStructureGroup("Another", fx.year(), fx.class5()));
		assertClosed(() -> feeService.createFeeStructure(fees.group(), fees.tuition(), new BigDecimal("500.00"), installments));
		assertClosed(() -> feeService.assignFee(secondEnrollment, fees.secondStructure(), null, null, null));
		assertClosed(() -> feeService.reviseAssignment(fees.assignment(), new BigDecimal("500.00"), "late discount", 7L));
		assertClosed(() -> feeService.generateInvoice(fees.assignment(), fees.installment()));

		// Nothing slipped through: still the one structure pair, one assignment and one invoice.
		assertThat(invoiceRepository.findAll()).hasSize(1);
		assertThat(studentFeeAssignmentRepository.findByStudentEnrollmentId(secondEnrollment.getId())).isEmpty();
	}

	@Test
	void feeSetupAssignmentRevisionAndInvoicingStillWorkInAnOpenYear() {
		Fixture fx = setUpFixture("open-fees");
		FeeSetup fees = setUpFees(fx);

		StudentFeeAssignment revised = feeService.reviseAssignment(fees.assignment(), new BigDecimal("500.00"), "late discount", 7L);
		feeService.createFeeStructureGroup("Another", fx.year(), fx.class5());

		assertThat(revised.getEffectiveAmount()).isEqualByComparingTo("9500.00");
		assertThat(invoiceRepository.findAll()).hasSize(1);
	}

	@Test
	void collectingAPaymentAgainstAnInvoiceInAClosedYearStillSucceeds() {
		Fixture fx = setUpFixture("closed-year-arrears");
		FeeSetup fees = setUpFees(fx);
		closeYear(fx.year());

		Payment payment = feeService.recordPayment(fx.year(), new BigDecimal("4000.00"), PaymentMethod.CASH, LocalDate.of(2025, 8, 1),
				"arrears", List.of(new AllocationInput(fees.invoice().getId(), new BigDecimal("4000.00"))));

		assertThat(payment.getId()).isNotNull();
		Invoice invoice = invoiceRepository.findById(fees.invoice().getId()).orElseThrow();
		assertThat(invoice.getAmountPaid()).isEqualByComparingTo("4000.00");
		assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
		assertThat(paymentRepository.findAll()).hasSize(1);
	}

	// ---- timetable ---------------------------------------------------------------------------------------

	@Test
	void timetableEntriesAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-timetable");
		Period period = timetableService.createPeriod(1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), false);
		closeYear(fx.year());
		Section section = sectionRepository.findById(fx.sectionA().getId()).orElseThrow();

		assertClosed(() -> timetableService.setEntry(section, fx.maths(), fx.teacher(), DayOfWeek.MONDAY, period));

		assertThat(timetableEntryRepository.findBySectionId(fx.sectionA().getId())).isEmpty();
	}

	@Test
	void timetableEntriesStillWorkInAnOpenYear() {
		Fixture fx = setUpFixture("open-timetable");
		Period period = timetableService.createPeriod(1, "Period 1", LocalTime.of(9, 0), LocalTime.of(9, 45), false);

		timetableService.setEntry(fx.sectionA(), fx.maths(), fx.teacher(), DayOfWeek.MONDAY, period);

		assertThat(timetableEntryRepository.findBySectionId(fx.sectionA().getId())).hasSize(1);
	}

	// ---- transport ---------------------------------------------------------------------------------------

	@Test
	void transportAssignmentAndRouteChangesAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-transport");
		Route route = routeRepository.save(new Route(fx.campus(), "Route 12", "R12", null));
		RouteStop stop = routeStopRepository.save(new RouteStop(route, "Gate 1", 1, null, null, null, null));
		RouteStop otherStop = routeStopRepository.save(new RouteStop(route, "Gate 2", 2, null, null, null, null));
		StudentTransportAssignment assignment =
				transportService.assignStudent(fx.enrollment(), route, stop, true, true, LocalDate.of(2025, 6, 1));
		Student second = studentService.admit(personRepository.save(new Person("Kabir", "Rao")), "ADM-closed-transport-2",
				LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment secondEnrollment = studentService.enroll(second, fx.year(), fx.sectionA(), 2, LocalDate.of(2025, 6, 1));
		closeYear(fx.year());

		assertClosed(() -> transportService.assignStudent(secondEnrollment, route, stop, true, true, LocalDate.of(2025, 7, 1)));
		assertClosed(() -> transportService.reassignRoute(assignment, route, otherStop, LocalDate.of(2025, 9, 1)));

		assertThat(studentTransportAssignmentRepository.findAll()).hasSize(1);
		StudentTransportAssignment unchanged = studentTransportAssignmentRepository.findById(assignment.getId()).orElseThrow();
		assertThat(unchanged.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
		assertThat(unchanged.getRouteStop().getId()).isEqualTo(stop.getId());
	}

	@Test
	void transportAssignmentAndRouteChangesStillWorkInAnOpenYear() {
		Fixture fx = setUpFixture("open-transport");
		Route route = routeRepository.save(new Route(fx.campus(), "Route 12", "R12", null));
		RouteStop stop = routeStopRepository.save(new RouteStop(route, "Gate 1", 1, null, null, null, null));
		RouteStop otherStop = routeStopRepository.save(new RouteStop(route, "Gate 2", 2, null, null, null, null));

		StudentTransportAssignment assignment =
				transportService.assignStudent(fx.enrollment(), route, stop, true, true, LocalDate.of(2025, 6, 1));
		StudentTransportAssignment reassigned = transportService.reassignRoute(assignment, route, otherStop, LocalDate.of(2025, 9, 1));

		assertThat(reassigned.getStatus()).isEqualTo(TransportAssignmentStatus.ACTIVE);
		assertThat(reassigned.getRouteStop().getId()).isEqualTo(otherStop.getId());
	}

	// ---- learning ----------------------------------------------------------------------------------------

	@Test
	void homeworkAndQuizAuthoringAreRejectedInAClosedYear() {
		Fixture fx = setUpFixture("closed-learning");
		LearningActor admin = new LearningActor(1L, personRepository.save(new Person("Admin", "User")).getId(), true, true, true, List.of());
		closeYear(fx.year());

		assertClosed(() -> homeworkService.assign(admin, fx.sectionA().getId(), fx.maths().getId(), "Fractions", null,
				LocalDate.of(2025, 9, 1), null));
		assertClosed(() -> quizService.createQuiz(admin, fx.sectionA().getId(), fx.maths().getId(), "Quiz 1", null,
				Instant.parse("2025-09-01T09:00:00Z"), Instant.parse("2025-09-01T10:00:00Z")));

		assertThat(homeworkService.list(admin, fx.sectionA().getId())).isEmpty();
		assertThat(quizService.list(admin, fx.sectionA().getId())).isEmpty();
	}

	@Test
	void homeworkAuthoringStillWorksInAnOpenYear() {
		Fixture fx = setUpFixture("open-learning");
		LearningActor admin = new LearningActor(1L, personRepository.save(new Person("Admin", "User")).getId(), true, true, true, List.of());

		Homework homework = homeworkService.assign(admin, fx.sectionA().getId(), fx.maths().getId(), "Fractions", null,
				LocalDate.of(2025, 9, 1), null);

		assertThat(homework.getId()).isNotNull();
	}

	// ---- close precondition ------------------------------------------------------------------------------

	@Test
	void aYearCannotBeClosedWhileStudentsAreStillEnrolledInIt() {
		Fixture fx = setUpFixture("close-blocked");
		Person other = personRepository.save(new Person("Rohan", "Das"));
		Student second = studentService.admit(other, "ADM-close-blocked-2", LocalDate.of(2025, 5, 1), null, null, null, null, null, null,
				"Indian", null, null, null, null);
		studentService.enroll(second, fx.year(), fx.sectionA(), 2, LocalDate.of(2025, 6, 1));

		assertThatThrownBy(() -> academicYearService.close(fx.year().getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("2 students are still enrolled in 2025-2026; promote or exit them before closing");

		assertThat(academicYearRepository.findById(fx.year().getId()).orElseThrow().isClosed()).isFalse();
	}

	@Test
	void theBlockedMessageIsSingularForOneStudent() {
		Fixture fx = setUpFixture("close-blocked-one");

		assertThatThrownBy(() -> academicYearService.close(fx.year().getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("1 student is still enrolled in 2025-2026; promote or exit them before closing");
	}

	@Test
	void aYearCanBeClosedOncePromotedOrExitedOrWhenItNeverHadStudents() {
		Fixture fx = setUpFixture("close-allowed");

		studentService.promote(fx.student(), fx.nextYear(), fx.nextYearSectionA(), 1, LocalDate.of(2026, 6, 1), false);
		AcademicYear closed = academicYearService.close(fx.year().getId());
		assertThat(closed.isClosed()).isTrue();

		// The target year still holds the promoted student, so it stays blocked; once they leave it can close too.
		assertThatThrownBy(() -> academicYearService.close(fx.nextYear().getId())).isInstanceOf(IllegalStateException.class);
		studentService.recordExit(fx.student(), StudentExitType.GRADUATION, LocalDate.of(2027, 3, 1), "done", null, null);
		assertThat(academicYearService.close(fx.nextYear().getId()).isClosed()).isTrue();
	}

	@Test
	void aFreshYearWithNoEnrolmentsCanBeClosed() {
		Organisation organisation = organisationRepository.save(new Organisation("Fresh School", "Fresh Trust", "close-fresh"));
		TenantContext.set(organisation.getId(), null);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));

		assertThat(academicYearService.close(year.getId()).isClosed()).isTrue();
	}
}
