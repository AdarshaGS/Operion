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
import com.operion.examination.ExaminationService.BandInput;
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
 * Proves publishReportCard aggregates marks across every ExamSchedule for the student's
 * class, resolves the grade via GradingScale, refuses to publish with a missing subject,
 * and rejects a duplicate publish. Per ai-context/erp-system-plan.md §3.3.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportCardTest {

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
	private ReportCardRepository reportCardRepository;

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

	@BeforeEach
	void setUpExaminationService() {
		examinationService = new ExaminationService(examRepository, examScheduleRepository, gradingScaleRepository,
				gradingScaleBandRepository, marksEntryRepository, reportCardRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Exam exam, ExamSchedule mathsSchedule, ExamSchedule scienceSchedule, StudentEnrollment enrollment, GradingScale gradingScale) {
	}

	/** Two 100-mark subjects; the test scores 90+85=175/200 = 87.5% to land in the A band, not A+. */
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
		Subject science = subjectRepository.save(new Subject("Science", "SCI"));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		Exam exam = examinationService.createExam(academicYear, "Half Yearly", ExamType.MID_TERM);
		ExamSchedule mathsSchedule = examinationService.addSchedule(exam, schoolClass, maths, LocalDate.of(2025, 9, 1), 100.0, 35.0);
		ExamSchedule scienceSchedule = examinationService.addSchedule(exam, schoolClass, science, LocalDate.of(2025, 9, 3), 100.0, 35.0);

		GradingScale gradingScale = examinationService.createGradingScale("CBSE Standard", true, List.of(
				new BandInput("A+", 90.0, "Excellent"),
				new BandInput("A", 80.0, "Very good"),
				new BandInput("B", 60.0, "Good"),
				new BandInput("F", 0.0, "Fail")));

		return new Fixture(exam, mathsSchedule, scienceSchedule, enrollment, gradingScale);
	}

	@Test
	void publishesAReportCardAggregatingAcrossAllSubjectsAndRejectsADuplicatePublish() {
		Fixture fixture = setUpFixture("report-card-school", "ADM-500");
		examinationService.enterMarks(fixture.mathsSchedule(), List.of(new MarkInput(fixture.enrollment(), 90.0, false, null)));
		examinationService.enterMarks(fixture.scienceSchedule(), List.of(new MarkInput(fixture.enrollment(), 85.0, false, null)));

		ReportCard reportCard = examinationService.publishReportCard(fixture.exam(), fixture.enrollment(), fixture.gradingScale());

		assertThat(reportCard.getTotalMarksObtained()).isEqualTo(175.0);
		assertThat(reportCard.getTotalMaxMarks()).isEqualTo(200.0);
		assertThat(reportCard.getPercentage()).isEqualTo(87.5);
		assertThat(reportCard.getOverallGrade()).isEqualTo("A");

		assertThatThrownBy(() -> examinationService.publishReportCard(fixture.exam(), fixture.enrollment(), fixture.gradingScale()))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesToPublishWithAMissingSubject() {
		Fixture fixture = setUpFixture("report-card-missing-subject-school", "ADM-501");
		examinationService.enterMarks(fixture.mathsSchedule(), List.of(new MarkInput(fixture.enrollment(), 90.0, false, null)));
		// Science marks never entered.

		assertThatThrownBy(() -> examinationService.publishReportCard(fixture.exam(), fixture.enrollment(), fixture.gradingScale()))
				.isInstanceOf(IllegalStateException.class);
	}
}
