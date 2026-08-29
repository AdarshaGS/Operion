package com.operion.attendance;

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
import com.operion.attendance.AttendanceService.StudentAttendanceMark;
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
 * Proves the register lifecycle (DRAFT -> SUBMITTED -> LOCKED) and its interaction with
 * marking/correction: a section+day can't be double-marked, corrections are allowed once
 * SUBMITTED but blocked once LOCKED, and every correction leaves a typed
 * AttendanceCorrection row plus a mirrored AuditLog entry. Per
 * ai-context/erp-system-plan.md §3.1.
 *
 * AttendanceService/AuditLogService are constructed by hand rather than @Import'd -
 * DataJpaTest's slice has no ObjectMapper bean, and there's no need to pull in Jackson
 * autoconfiguration just to unit-test this service.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentAttendanceLifecycleTest {

	private AttendanceService attendanceService;
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
	private StudentAttendanceRepository studentAttendanceRepository;

	@Autowired
	private ClassAttendanceRegisterRepository classAttendanceRegisterRepository;

	@Autowired
	private AttendanceCorrectionRepository attendanceCorrectionRepository;

	@Autowired
	private StaffAttendanceRepository staffAttendanceRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUpAttendanceService() {
		attendanceService = new AttendanceService(studentAttendanceRepository, classAttendanceRegisterRepository,
				attendanceCorrectionRepository, staffAttendanceRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
			studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(AcademicYear academicYear, Section section, StudentEnrollment enrollment) {
	}

	private Fixture setUpFixture(String orgSlug, String admissionNumber) {
		Organisation organisation =
				organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear = academicYearRepository.save(
				new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		return new Fixture(academicYear, section, enrollment);
	}

	@Test
	void marksAttendanceAndRejectsDoubleMarkingTheSameDay() {
		Fixture fixture = setUpFixture("attendance-mark-school", "ADM-100");
		LocalDate date = LocalDate.of(2025, 7, 1);
		List<StudentAttendanceMark> marks =
				List.of(new StudentAttendanceMark(fixture.enrollment(), AttendanceStatus.PRESENT, false, null));

		ClassAttendanceRegister register =
				attendanceService.markDailyAttendance(fixture.academicYear(), fixture.section(), date, marks);

		assertThat(register.getRegisterStatus()).isEqualTo(ClassAttendanceRegisterStatus.DRAFT);
		List<StudentAttendance> entries =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(fixture.section().getId(), date);
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
		assertThat(entries.get(0).getSchoolClass().getId()).isEqualTo(fixture.section().getSchoolClass().getId());

		assertThatThrownBy(() -> attendanceService.markDailyAttendance(fixture.academicYear(), fixture.section(), date, marks))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void correctionIsAllowedAfterSubmitButBlockedAfterLock() {
		Fixture fixture = setUpFixture("attendance-correct-school", "ADM-101");
		LocalDate date = LocalDate.of(2025, 7, 2);
		List<StudentAttendanceMark> marks =
				List.of(new StudentAttendanceMark(fixture.enrollment(), AttendanceStatus.ABSENT, false, null));

		ClassAttendanceRegister draftRegister =
				attendanceService.markDailyAttendance(fixture.academicYear(), fixture.section(), date, marks);
		StudentAttendance attendance =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(fixture.section().getId(), date).get(0);

		assertThatThrownBy(() -> attendanceService.lockRegister(draftRegister)).isInstanceOf(IllegalStateException.class);

		ClassAttendanceRegister submittedRegister = attendanceService.submitRegister(draftRegister);
		assertThat(submittedRegister.getRegisterStatus()).isEqualTo(ClassAttendanceRegisterStatus.SUBMITTED);

		StudentAttendance corrected = attendanceService.correct(attendance, AttendanceStatus.PRESENT, "Marked absent by mistake");
		assertThat(corrected.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);

		List<AttendanceCorrection> corrections = attendanceCorrectionRepository.findByStudentAttendanceId(attendance.getId());
		assertThat(corrections).hasSize(1);
		assertThat(corrections.get(0).getPreviousStatus()).isEqualTo(AttendanceStatus.ABSENT);
		assertThat(corrections.get(0).getNewStatus()).isEqualTo(AttendanceStatus.PRESENT);
		assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getEntityType().equals("StudentAttendance"));

		ClassAttendanceRegister lockedRegister = attendanceService.lockRegister(submittedRegister);
		assertThat(lockedRegister.getRegisterStatus()).isEqualTo(ClassAttendanceRegisterStatus.LOCKED);

		StudentAttendance reloaded = studentAttendanceRepository.findById(attendance.getId()).orElseThrow();
		assertThatThrownBy(() -> attendanceService.correct(reloaded, AttendanceStatus.ABSENT, "Too late"))
				.isInstanceOf(IllegalStateException.class);
	}
}
