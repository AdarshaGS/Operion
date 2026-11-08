package com.operion.examination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.Subject;
import com.operion.academic.SubjectRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.ExaminationService.MarkInput;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.modules.AppModule;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.AdmissionFeeClearanceMode;
import com.operion.student.AdmissionFeeGate;
import com.operion.student.AdmissionFeePendingException;
import com.operion.student.AdmissionSettings;
import com.operion.student.AdmissionSettingsRepository;
import com.operion.student.FeeClearanceStatus;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollment;
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
 * Real rows: the admission-fee gate on ExaminationService. In BLOCK mode a PENDING student cannot have marks
 * entered, corrected (before or after publication) or a report card published; one PENDING enrollment in a
 * batch rejects the whole batch; and TRACK, OFF, FEES-module-off and every other clearance status are allowed.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class, AdmissionFeeGate.class, ModuleAccessService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExaminationAdmissionFeeGateTest {

	@Autowired private AdmissionFeeGate admissionFeeGate;
	@Autowired private ModuleAccessService moduleAccessService;
	@Autowired private AdmissionSettingsRepository admissionSettingsRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private GradeLevelRepository gradeLevelRepository;
	@Autowired private SchoolClassRepository schoolClassRepository;
	@Autowired private SectionRepository sectionRepository;
	@Autowired private SubjectRepository subjectRepository;
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
	@Autowired private StudentRepository studentRepository;
	@Autowired private StudentEnrollmentRepository studentEnrollmentRepository;
	@Autowired private StudentDocumentRepository studentDocumentRepository;
	@Autowired private StudentExitRepository studentExitRepository;
	@Autowired private StudentIdGenerator studentIdGenerator;

	private ExaminationService examinationService;
	private StudentService studentService;

	private Organisation organisation;
	private Exam exam;
	private ExamSchedule maths;
	private GradingScale gradingScale;
	private StudentEnrollment meera;
	private StudentEnrollment ravi;
	private int sequence;

	@BeforeEach
	void setUp() {
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository, gradingScaleBandRepository,
				marksEntryRepository, marksEntryRegisterRepository, reportCardRepository, examinationSettingsRepository, auditLogService,
				admissionFeeGate);
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository, studentExitRepository,
				sectionRepository, gradeLevelRepository, null, null, studentIdGenerator, auditLogService,
				new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));

		organisation = organisationRepository.save(new Organisation("Gate School", "Gate Trust", "exam-gate-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Subject mathsSubject = subjectRepository.save(new Subject("Mathematics", "MATH"));

		meera = enrol(year, section, "Meera");
		ravi = enrol(year, section, "Ravi");

		exam = examinationService.createExam(year, "Half Yearly", ExamType.MID_TERM);
		maths = examinationService.addSchedule(exam, schoolClass, mathsSubject, LocalDate.of(2025, 9, 1), 100.0, 35.0);
		gradingScale = examinationService.createGradingScale("Standard", true,
				List.of(new BandInput("A", 80.0, "Very good"), new BandInput("F", 0.0, "Fail")));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private StudentEnrollment enrol(AcademicYear year, Section section, String name) {
		Person person = personRepository.save(new Person(name, "Test"));
		Student student = studentService.admit(person, "ADM-" + System.nanoTime() + "-" + ++sequence, LocalDate.of(2025, 5, 1), null, null, null,
				null, null, null, "Indian", null, null, null, null);
		return studentService.enroll(student, year, section, sequence, LocalDate.of(2025, 6, 1));
	}

	private void setMode(AdmissionFeeClearanceMode mode) {
		AdmissionSettings settings = admissionSettingsRepository.findByOrganisationId(organisation.getId()).orElseGet(AdmissionSettings::new);
		settings.setFeeClearanceMode(mode);
		admissionSettingsRepository.save(settings);
	}

	/** Writes the status straight to the row, as the finance code would, and returns the freshly loaded enrollment. */
	private StudentEnrollment setStatus(StudentEnrollment enrollment, FeeClearanceStatus status) {
		StudentEnrollment stored = studentEnrollmentRepository.findById(enrollment.getId()).orElseThrow();
		stored.changeFeeClearanceStatus(status);
		return studentEnrollmentRepository.save(stored);
	}

	private MarksEntry freshEntry(MarksEntry entry) {
		return marksEntryRepository.findById(entry.getId()).orElseThrow();
	}

	private static MarkInput mark(StudentEnrollment enrollment, double marks) {
		return new MarkInput(enrollment, marks, false, null);
	}

	@Test
	void enteringMarksIsRejectedForAPendingStudentInBlockModeAndSavesNothing() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment pending = setStatus(meera, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> examinationService.enterMarks(maths, List.of(mark(pending, 80.0))))
				.isInstanceOfSatisfying(AdmissionFeePendingException.class, e -> assertThat(e.getEnrollmentIds()).containsExactly(meera.getId()));
		assertThat(marksEntryRepository.findAll()).isEmpty();
	}

	@Test
	void onePendingEnrollmentInABatchRejectsTheWholeBatch() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment pending = setStatus(ravi, FeeClearanceStatus.PENDING);
		StudentEnrollment cleared = setStatus(meera, FeeClearanceStatus.CLEARED);

		assertThatThrownBy(() -> examinationService.enterMarks(maths, List.of(mark(cleared, 70.0), mark(pending, 60.0))))
				.isInstanceOfSatisfying(AdmissionFeePendingException.class, e -> assertThat(e.getEnrollmentIds()).containsExactly(ravi.getId()));
		assertThat(marksEntryRepository.findAll()).isEmpty();
	}

	@Test
	void correctingMarksIsRejectedForAPendingStudentInBlockMode() {
		MarksEntry entry = examinationService.enterMarks(maths, List.of(mark(meera, 60.0))).get(0);
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(meera, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> examinationService.correctMarks(freshEntry(entry), 65.0, false, "Re-checked"))
				.isInstanceOf(AdmissionFeePendingException.class);
		assertThat(freshEntry(entry).getMarksObtained()).isEqualTo(60.0);
	}

	@Test
	void publishingAReportCardIsRejectedForAPendingStudentInBlockMode() {
		examinationService.enterMarks(maths, List.of(mark(meera, 90.0)));
		examinationService.submitMarksRegister(maths);
		examinationService.approveMarksRegister(maths);
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment pending = setStatus(meera, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> examinationService.publishReportCard(exam, pending, gradingScale))
				.isInstanceOf(AdmissionFeePendingException.class);
		assertThat(reportCardRepository.findAll()).isEmpty();
	}

	@Test
	void correctingMarksAfterPublicationIsRejectedForAPendingStudentInBlockMode() {
		MarksEntry entry = examinationService.enterMarks(maths, List.of(mark(meera, 90.0))).get(0);
		examinationService.submitMarksRegister(maths);
		examinationService.approveMarksRegister(maths);
		examinationService.publishReportCard(exam, meera, gradingScale);
		setMode(AdmissionFeeClearanceMode.BLOCK);
		setStatus(meera, FeeClearanceStatus.PENDING);

		assertThatThrownBy(() -> examinationService.correctMarksAfterPublish(freshEntry(entry), 50.0, false, "Re-checked"))
				.isInstanceOf(AdmissionFeePendingException.class);
		assertThat(freshEntry(entry).getMarksObtained()).isEqualTo(90.0);
	}

	@Test
	void trackAndOffModesAllowAPendingStudent() {
		StudentEnrollment pending = setStatus(meera, FeeClearanceStatus.PENDING);
		setMode(AdmissionFeeClearanceMode.TRACK);
		assertThatCode(() -> examinationService.enterMarks(maths, List.of(mark(pending, 80.0)))).doesNotThrowAnyException();

		MarksEntry entry = marksEntryRepository.findAll().get(0);
		setMode(AdmissionFeeClearanceMode.OFF);
		assertThatCode(() -> examinationService.correctMarks(freshEntry(entry), 85.0, false, "Re-checked")).doesNotThrowAnyException();
	}

	@Test
	void aPendingStudentIsAllowedWhenTheFeesModuleIsOff() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		StudentEnrollment pending = setStatus(meera, FeeClearanceStatus.PENDING);
		moduleAccessService.setEnabled(organisation.getId(), AppModule.FEES, false, "test");

		assertThatCode(() -> examinationService.enterMarks(maths, List.of(mark(pending, 80.0)))).doesNotThrowAnyException();
	}

	@Test
	void notRequiredClearedAndExemptedStudentsAreAllowedInBlockMode() {
		setMode(AdmissionFeeClearanceMode.BLOCK);
		assertThatCode(() -> examinationService.enterMarks(maths, List.of(mark(setStatus(meera, FeeClearanceStatus.NOT_REQUIRED), 70.0))))
				.doesNotThrowAnyException();
		StudentEnrollment cleared = setStatus(ravi, FeeClearanceStatus.CLEARED);
		MarksEntry entry = marksEntryRepository.findAll().get(0);
		assertThatCode(() -> examinationService.correctMarks(freshEntry(entry), 71.0, false, "Typo")).doesNotThrowAnyException();
		setStatus(meera, FeeClearanceStatus.EXEMPTED);
		assertThatCode(() -> examinationService.correctMarks(freshEntry(entry), 72.0, false, "Typo again")).doesNotThrowAnyException();
		assertThat(cleared.getFeeClearanceStatus()).isEqualTo(FeeClearanceStatus.CLEARED);
	}
}
