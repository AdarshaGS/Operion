package com.operion.attendance;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.operion.customfield.CustomFieldDefinitionRepository;
import com.operion.customfield.CustomFieldService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationRecipient;
import com.operion.communication.NotificationRecipientRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRelationshipType;
import com.operion.parent.GuardianRepository;
import com.operion.parent.StudentGuardian;
import com.operion.parent.StudentGuardianRepository;
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
 * Proves the attendance half of #165 goes through the real notification pipeline (a
 * NotificationRecipient row, not a no-op) and respects StudentGuardian.canReceiveCommunication
 * the same way FeeReminderServiceTest proves it for fee reminders.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, StudentIdGenerator.class, CommunicationService.class, AttendanceAlertService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttendanceAlertServiceTest {

	private AttendanceService attendanceService;
	private StudentService studentService;

	@Autowired
	private AttendanceAlertService attendanceAlertService;

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

	@Autowired
	private CustomFieldDefinitionRepository customFieldDefinitionRepository;

	@Autowired
	private GuardianRepository guardianRepository;

	@Autowired
	private StudentGuardianRepository studentGuardianRepository;

	@Autowired
	private NotificationRecipientRepository notificationRecipientRepository;

	@BeforeEach
	void setUp() {
		attendanceService = new AttendanceService(studentAttendanceRepository, classAttendanceRegisterRepository,
				attendanceCorrectionRepository, staffAttendanceRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository,
				studentExitRepository, null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()),
				new CustomFieldService(customFieldDefinitionRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Student student, StudentEnrollment enrollment, AcademicYear academicYear, Section section) {
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
		Person studentPerson = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				studentPerson, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));
		return new Fixture(student, enrollment, academicYear, section);
	}

	@Test
	void notifiesAGuardianWhoOptedIntoCommunicationWhenAStudentIsMarkedAbsent() {
		Fixture fixture = setUpFixture("attendance-alert-school", "ADM-370");
		Person guardianPerson = personRepository.save(new Person("Ravi", "Nair"));
		guardianPerson.setEmail("ravi.nair@example.com");
		personRepository.save(guardianPerson);
		Guardian guardian = guardianRepository.save(new Guardian(guardianPerson, null));
		studentGuardianRepository.save(
				new StudentGuardian(fixture.student(), guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1));

		attendanceService.markDailyAttendance(fixture.academicYear(), fixture.section(), LocalDate.of(2025, 7, 1),
				List.of(new StudentAttendanceMark(fixture.enrollment(), AttendanceStatus.ABSENT, false, null)));
		StudentAttendance attendance =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(fixture.section().getId(), LocalDate.of(2025, 7, 1)).get(0);

		int notified = attendanceAlertService.notifyAbsence(attendance);

		assertThat(notified).isEqualTo(1);
		List<NotificationRecipient> recipients = notificationRecipientRepository.findByPersonIdOrderByCreatedAtDesc(guardianPerson.getId());
		assertThat(recipients).hasSize(1);
		assertThat(recipients.get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);
	}

	@Test
	void skipsAGuardianWhoHasNotOptedIntoCommunication() {
		Fixture fixture = setUpFixture("attendance-alert-optout-school", "ADM-371");
		Person guardianPerson = personRepository.save(new Person("Asha", "Nair"));
		guardianPerson.setEmail("asha.nair@example.com");
		personRepository.save(guardianPerson);
		Guardian guardian = guardianRepository.save(new Guardian(guardianPerson, null));
		studentGuardianRepository.save(
				new StudentGuardian(fixture.student(), guardian, GuardianRelationshipType.MOTHER, true, true, true, false, 1));

		attendanceService.markDailyAttendance(fixture.academicYear(), fixture.section(), LocalDate.of(2025, 7, 1),
				List.of(new StudentAttendanceMark(fixture.enrollment(), AttendanceStatus.ABSENT, false, null)));
		StudentAttendance attendance =
				studentAttendanceRepository.findBySectionIdAndAttendanceDate(fixture.section().getId(), LocalDate.of(2025, 7, 1)).get(0);

		int notified = attendanceAlertService.notifyAbsence(attendance);

		assertThat(notified).isEqualTo(0);
		assertThat(notificationRecipientRepository.findByPersonIdOrderByCreatedAtDesc(guardianPerson.getId())).isEmpty();
	}
}
