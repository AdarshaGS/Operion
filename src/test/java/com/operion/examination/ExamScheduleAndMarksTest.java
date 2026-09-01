package com.operion.examination;

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
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
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
 * Proves marks entry rejects double-entry for the same (schedule, enrollment), and that
 * correction mutates the row in place while mirroring to the shared AuditLog - see
 * MarksEntry's class doc for why this diverges from Attendance's typed-correction-table
 * pattern.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExamScheduleAndMarksTest {

	private ExaminationService examinationService;

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
	private SubjectRepository subjectRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private ExamScheduleRepository examScheduleRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private GradingScaleBandRepository gradingScaleBandRepository;

	@Autowired
	private MarksEntryRepository marksEntryRepository;

	@Autowired
	private MarksEntryRegisterRepository marksEntryRegisterRepository;

	@Autowired
	private ReportCardRepository reportCardRepository;

	@Autowired
	private ExaminationSettingsRepository examinationSettingsRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

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

	@BeforeEach
	void setUpExaminationService() {
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, marksEntryRegisterRepository, reportCardRepository,
				examinationSettingsRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(ExamSchedule schedule, StudentEnrollment enrollment) {
	}

	private Fixture setUpFixture(String orgSlug, String admissionNumber) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Subject maths = subjectRepository.save(new Subject("Mathematics", "MATH"));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		Exam exam = examinationService.createExam(academicYear, "Term 1 Unit Test", ExamType.UNIT_TEST);
		ExamSchedule schedule =
				examinationService.addSchedule(exam, schoolClass, maths, LocalDate.of(2025, 8, 1), 100.0, 35.0);

		return new Fixture(schedule, enrollment);
	}

	@Test
	void entersMarksAndRejectsDoubleEntryForTheSameStudent() {
		Fixture fixture = setUpFixture("marks-entry-school", "ADM-400");
		List<MarkInput> marks = List.of(new MarkInput(fixture.enrollment(), 78.0, false, null));

		List<MarksEntry> entered = examinationService.enterMarks(fixture.schedule(), marks);
		assertThat(entered).hasSize(1);
		assertThat(entered.get(0).getMarksObtained()).isEqualTo(78.0);

		assertThatThrownBy(() -> examinationService.enterMarks(fixture.schedule(), marks)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void correctingMarksMutatesInPlaceAndWritesToTheAuditLog() {
		Fixture fixture = setUpFixture("marks-correction-school", "ADM-401");
		MarksEntry entry = examinationService.enterMarks(fixture.schedule(), List.of(new MarkInput(fixture.enrollment(), 60.0, false, null))).get(0);

		MarksEntry corrected = examinationService.correctMarks(entry, 65.0, false, "Re-checked");

		assertThat(corrected.getId()).isEqualTo(entry.getId());
		assertThat(corrected.getMarksObtained()).isEqualTo(65.0);
		assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getEntityType().equals("MarksEntry"));
	}

	@Test
	void absentMarksNormalizeToZero() {
		Fixture fixture = setUpFixture("marks-absent-school", "ADM-402");

		MarksEntry entry = examinationService.enterMarks(fixture.schedule(), List.of(new MarkInput(fixture.enrollment(), null, true, "Sick leave"))).get(0);

		assertThat(entry.isAbsent()).isTrue();
		assertThat(entry.getMarksObtained()).isEqualTo(0.0);
	}

	@Test
	void submitAndApproveMoveTheRegisterThroughItsLifecycleAndBlockFurtherEntryOnceSubmitted() {
		Fixture fixture = setUpFixture("marks-register-school", "ADM-403");
		examinationService.enterMarks(fixture.schedule(), List.of(new MarkInput(fixture.enrollment(), 78.0, false, null)));

		MarksEntryRegister submitted = examinationService.submitMarksRegister(fixture.schedule());
		assertThat(submitted.getRegisterStatus()).isEqualTo(MarksEntryRegisterStatus.SUBMITTED);

		assertThatThrownBy(() -> examinationService.enterMarks(fixture.schedule(), List.of()))
				.isInstanceOf(IllegalStateException.class);

		MarksEntryRegister approved = examinationService.approveMarksRegister(fixture.schedule());
		assertThat(approved.getRegisterStatus()).isEqualTo(MarksEntryRegisterStatus.APPROVED);
		assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getEntityType().equals("MarksEntryRegister") && log.getAction().equals("APPROVED"));
	}

	@Test
	void approvingWithoutASubmittedRegisterIsRejected() {
		Fixture fixture = setUpFixture("marks-register-unsubmitted-school", "ADM-404");
		examinationService.enterMarks(fixture.schedule(), List.of(new MarkInput(fixture.enrollment(), 78.0, false, null)));

		assertThatThrownBy(() -> examinationService.approveMarksRegister(fixture.schedule())).isInstanceOf(IllegalStateException.class);
	}
}
