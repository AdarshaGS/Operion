package com.operion.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.attendance.AttendanceService;
import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.ClassAttendanceRegisterRepository;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.attendance.StudentAttendance;
import com.operion.attendance.StudentAttendanceRepository;
import com.operion.attendance.AttendanceCorrectionRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.communication.Announcement;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AudienceType;
import com.operion.examination.Exam;
import com.operion.examination.ExamRepository;
import com.operion.examination.ExamType;
import com.operion.examination.ReportCard;
import com.operion.examination.ReportCardRepository;
import com.operion.finance.FeeCategory;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeCategoryType;
import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureGroup;
import com.operion.finance.FeeStructureGroupRepository;
import com.operion.finance.FeeStructureInstallment;
import com.operion.finance.FeeStructureInstallmentRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import com.operion.finance.StudentFeeAssignment;
import com.operion.finance.StudentFeeAssignmentRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRelationshipType;
import com.operion.parent.ParentService;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
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
 * Covers #207: every read is scoped to the caller's own linked child, and a student the
 * caller isn't linked to is rejected rather than silently returning data.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ParentService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuardianPortalServiceTest {

	private GuardianPortalService guardianPortalService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

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
	private ParentService parentService;

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
	private FeeCategoryRepository feeCategoryRepository;

	@Autowired
	private FeeStructureGroupRepository feeStructureGroupRepository;

	@Autowired
	private FeeStructureRepository feeStructureRepository;

	@Autowired
	private FeeStructureInstallmentRepository feeStructureInstallmentRepository;

	@Autowired
	private StudentFeeAssignmentRepository studentFeeAssignmentRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private ReportCardRepository reportCardRepository;

	@Autowired
	private AnnouncementRepository announcementRepository;

	@BeforeEach
	void setUp() {
		AttendanceService attendanceService = new AttendanceService(studentAttendanceRepository, classAttendanceRegisterRepository,
				attendanceCorrectionRepository, staffAttendanceRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		guardianPortalService = new GuardianPortalService(membershipRepository, parentServiceGuardianRepository(), studentGuardianRepository(),
				studentEnrollmentRepository, attendanceService, studentAttendanceRepository, invoiceRepository, reportCardRepository,
				announcementRepository);
	}

	// ParentService already exposes a GuardianRepository/StudentGuardianRepository internally,
	// but GuardianPortalService needs its own references - both are plain @Repository beans,
	// so pulling them straight from the Spring context (rather than @Autowired fields, to keep
	// the field list above focused on what the fixtures directly touch) keeps this test file
	// from having to redeclare every dependency GuardianPortalService happens to need.
	@Autowired
	private com.operion.parent.GuardianRepository guardianRepositoryField;

	@Autowired
	private com.operion.parent.StudentGuardianRepository studentGuardianRepositoryField;

	private com.operion.parent.GuardianRepository parentServiceGuardianRepository() {
		return guardianRepositoryField;
	}

	private com.operion.parent.StudentGuardianRepository studentGuardianRepository() {
		return studentGuardianRepositoryField;
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(
			User user, Student student, StudentEnrollment enrollment, Guardian guardian, Campus campus, AcademicYear year,
			SchoolClass schoolClass, Section section) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));

		Person studentPerson = personRepository.save(new Person("Ira", "Shah"));
		Student student = studentRepository.save(new Student(studentPerson, "STU-400", "ADM-400", LocalDate.of(2025, 5, 1), null, null,
				null, null, null, null, null, null, null, null, null, null));
		StudentEnrollment enrollment =
				studentEnrollmentRepository.save(new StudentEnrollment(student, year, section, 1, LocalDate.of(2025, 6, 1)));

		Person guardianPerson = personRepository.save(new Person("Vikram", "Shah"));
		Guardian guardian = parentService.getOrCreateGuardian(guardianPerson, "Engineer");
		parentService.linkGuardian(student, guardian, GuardianRelationshipType.FATHER, true, true, true, true, 1);

		User user = userRepository.save(new User("guardian-" + slug + "@example.com", null, "hash"));
		Role role = roleRepository.save(new Role("Guardian", "portal access", false, true, true));
		membershipRepository.save(new OrganisationMembership(user, guardianPerson, role, campus));

		return new Fixture(user, student, enrollment, guardian, campus, year, schoolClass, section);
	}

	@Test
	void attendanceCombinesTodaysStatusAndMonthlySummary() {
		Fixture fixture = setUpFixture("guardian-portal-attendance");
		LocalDate today = LocalDate.now();
		studentAttendanceRepository.save(new StudentAttendance(fixture.enrollment(), fixture.year(), fixture.schoolClass(),
				fixture.section(), today, AttendanceStatus.PRESENT, false, null));

		GuardianAttendanceSummary summary = guardianPortalService.attendanceFor(fixture.user().getId(), fixture.student().getId());

		assertThat(summary.todayStatus()).isEqualTo(AttendanceStatus.PRESENT);
		assertThat(summary.monthly().totalMarkedDays()).isEqualTo(1);
		assertThat(summary.monthly().presentCount()).isEqualTo(1);
	}

	@Test
	void feesReturnsInvoicesForTheChildsCurrentEnrollment() {
		Fixture fixture = setUpFixture("guardian-portal-fees");
		FeeCategory category = feeCategoryRepository.save(new FeeCategory("TUITION", "Tuition", null, FeeCategoryType.GENERAL));
		FeeStructureGroup group =
				feeStructureGroupRepository.save(new FeeStructureGroup("Grade 5 Fees", fixture.year(), fixture.schoolClass()));
		FeeStructure structure = feeStructureRepository.save(new FeeStructure(group, category, new BigDecimal("10000.00")));
		FeeStructureInstallment installment =
				feeStructureInstallmentRepository.save(new FeeStructureInstallment(structure, 1, LocalDate.of(2025, 7, 1), new BigDecimal("10000.00")));
		StudentFeeAssignment assignment = studentFeeAssignmentRepository.save(
				new StudentFeeAssignment(fixture.enrollment(), structure, new BigDecimal("10000.00"), BigDecimal.ZERO,
						new BigDecimal("10000.00"), null, null));
		invoiceRepository.save(
				new Invoice(fixture.year(), assignment, installment, "INV-001", new BigDecimal("10000.00"), LocalDate.of(2025, 7, 1)));

		var fees = guardianPortalService.feesFor(fixture.user().getId(), fixture.student().getId());

		assertThat(fees).hasSize(1);
		assertThat(fees.get(0).invoiceNumber()).isEqualTo("INV-001");
		assertThat(fees.get(0).outstanding()).isEqualByComparingTo("10000.00");
	}

	@Test
	void reportCardsExcludesStaleAndUnpublishedOnes() {
		Fixture fixture = setUpFixture("guardian-portal-report-cards");
		Exam exam = examRepository.save(new Exam(fixture.year(), "Term 1", ExamType.MID_TERM));
		ReportCard published =
				reportCardRepository.save(new ReportCard(exam, fixture.enrollment(), 450.0, 500.0, 90.0, "A", true));
		ReportCard stale = reportCardRepository.save(new ReportCard(exam, fixture.enrollment(), 400.0, 500.0, 80.0, "B", true));
		stale.markStale();
		reportCardRepository.save(stale);

		var reportCards = guardianPortalService.reportCardsFor(fixture.user().getId(), fixture.student().getId());

		assertThat(reportCards).extracting(GuardianReportCardSummary::reportCardId).containsExactly(published.getId());
	}

	@Test
	void announcementsIncludesOrgWideAndSameCampusOnly() {
		Fixture fixture = setUpFixture("guardian-portal-announcements");
		Campus otherCampus = campusRepository.save(new Campus("Other Campus", "OTHER"));

		Announcement orgWide = new Announcement(null, "Org notice", "body", AudienceType.ORG, null);
		orgWide.publish();
		announcementRepository.save(orgWide);

		Announcement sameCampus = new Announcement(fixture.campus(), "Campus notice", "body", AudienceType.CAMPUS, null);
		sameCampus.publish();
		announcementRepository.save(sameCampus);

		Announcement otherCampusNotice = new Announcement(otherCampus, "Other campus notice", "body", AudienceType.CAMPUS, null);
		otherCampusNotice.publish();
		announcementRepository.save(otherCampusNotice);

		Announcement staffOnly = new Announcement(null, "Staff notice", "body", AudienceType.STAFF, null);
		staffOnly.publish();
		announcementRepository.save(staffOnly);

		Announcement draft = new Announcement(null, "Draft notice", "body", AudienceType.ORG, null);
		announcementRepository.save(draft);

		var announcements = guardianPortalService.announcementsFor(fixture.user().getId());

		assertThat(announcements).extracting(PortalAnnouncementSummary::title).containsExactlyInAnyOrder("Org notice", "Campus notice");
	}

	@Test
	void rejectsAccessToAStudentTheCallerIsNotLinkedTo() {
		Fixture fixture = setUpFixture("guardian-portal-unauthorized");
		Person otherStudentPerson = personRepository.save(new Person("Other", "Kid"));
		Student otherStudent = studentRepository.save(new Student(otherStudentPerson, "STU-401", "ADM-401", LocalDate.of(2025, 5, 1),
				null, null, null, null, null, null, null, null, null, null, null, null));

		assertThatThrownBy(() -> guardianPortalService.attendanceFor(fixture.user().getId(), otherStudent.getId()))
				.isInstanceOf(AuthorizationDeniedException.class);
	}
}
