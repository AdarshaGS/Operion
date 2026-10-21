package com.operion.portal;

import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.calendar.WorkingDayService;
import com.operion.organisation.OrganisationConfigurationRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentType;
import com.operion.attendance.StaffAttendanceRepository;
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
import com.operion.hr.EmploymentType;
import com.operion.hr.HrService;
import com.operion.hr.JobApplicationRepository;
import com.operion.hr.LeaveBalanceRepository;
import com.operion.hr.LeaveRequestRepository;
import com.operion.hr.LeaveType;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.StaffAssignmentRepository;
import com.operion.hr.StaffBankDetailRepository;
import com.operion.hr.StaffDocumentRepository;
import com.operion.hr.StaffExitRepository;
import com.operion.hr.StaffProfile;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
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
 * Covers #208: sections/leave reads and the raise-leave-request write are all scoped to
 * the caller's own StaffProfile/TeacherAssignments, never an arbitrary staff member's.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeacherPortalServiceTest {

	private TeacherPortalService teacherPortalService;
	private HrService hrService;

	@Autowired
	private WorkingCalendarEntryRepository workingCalendarEntryRepository;

	@Autowired
	private OrganisationConfigurationRepository organisationConfigurationRepository;

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
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private DesignationRepository designationRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private LeaveTypeRepository leaveTypeRepository;

	@Autowired
	private LeaveBalanceRepository leaveBalanceRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	@Autowired
	private StaffDocumentRepository staffDocumentRepository;

	@Autowired
	private JobApplicationRepository jobApplicationRepository;

	@Autowired
	private StaffAssignmentRepository staffAssignmentRepository;

	@Autowired
	private StaffExitRepository staffExitRepository;

	@Autowired
	private StaffBankDetailRepository staffBankDetailRepository;

	@Autowired
	private StaffAttendanceRepository staffAttendanceRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@BeforeEach
	void setUp() {
		hrService = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new WorkingDayService(workingCalendarEntryRepository, organisationConfigurationRepository),
				new AuditLogService(auditLogRepository, new ObjectMapper()));
		teacherPortalService = new TeacherPortalService(membershipRepository, teacherAssignmentRepository, staffProfileRepository,
				hrService, leaveRequestRepository, leaveBalanceRepository, leaveTypeRepository, academicYearRepository);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(User user, StaffProfile staffProfile, Section section, AcademicYear year) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear year = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, "PRIMARY"));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(year, campus, grade, null));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, "Room 1"));

		Person teacherPerson = personRepository.save(new Person("Meera", "Nair"));
		Designation designation = designationRepository.save(new Designation("Teacher"));
		Department department = departmentRepository.save(new Department("Science"));
		StaffProfile staffProfile = hrService.createStaffProfile(
				teacherPerson, campus, "EMP-" + System.nanoTime(), designation, department, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT);
		teacherAssignmentRepository.save(
				new TeacherAssignment(year, section, null, teacherPerson, TeacherAssignmentType.HOMEROOM, LocalDate.of(2025, 6, 1)));

		User user = userRepository.save(new User("teacher-" + slug + "@example.com", null, "hash"));
		Role role = roleRepository.save(new Role("Teacher", "portal access", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, teacherPerson, role, campus));

		return new Fixture(user, staffProfile, section, year);
	}

	@Test
	void sectionsReturnsOnlyTheCallersOwnActiveAssignments() {
		Fixture fixture = setUpFixture("teacher-portal-sections");

		var sections = teacherPortalService.sectionsFor(fixture.user().getId());

		assertThat(sections).hasSize(1);
		assertThat(sections.get(0).sectionId()).isEqualTo(fixture.section().getId());
		assertThat(sections.get(0).assignmentType()).isEqualTo("HOMEROOM");
	}

	@Test
	void leaveBalanceReflectsAllocationAndApprovedUsage() {
		Fixture fixture = setUpFixture("teacher-portal-leave-balance");
		LeaveType leaveType = hrService.createLeaveType("CASUAL", "Casual Leave", 12.0);
		hrService.allocateBalance(fixture.staffProfile(), leaveType, fixture.year(), 12.0);

		var balances = teacherPortalService.leaveBalanceFor(fixture.user().getId(), fixture.year().getId());

		assertThat(balances).extracting(TeacherLeaveBalanceSummary::leaveTypeId).contains(leaveType.getId());
		var casual = balances.stream().filter(b -> b.leaveTypeId().equals(leaveType.getId())).findFirst().orElseThrow();
		assertThat(casual.allocatedDays()).isEqualTo(12.0);
		assertThat(casual.remainingDays()).isEqualTo(12.0);
	}

	@Test
	void raiseLeaveRequestCreatesARequestAgainstTheCallersOwnStaffProfile() {
		Fixture fixture = setUpFixture("teacher-portal-raise-leave");
		LeaveType leaveType = hrService.createLeaveType("CASUAL", "Casual Leave", 12.0);
		hrService.allocateBalance(fixture.staffProfile(), leaveType, fixture.year(), 12.0);

		var summary = teacherPortalService.raiseLeaveRequest(fixture.user().getId(), leaveType.getId(), fixture.year().getId(),
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, "Family event");

		assertThat(summary.status()).isEqualTo("PENDING");
		assertThat(leaveRequestRepository.findByStaffProfileId(fixture.staffProfile().getId())).hasSize(1);
	}

	@Test
	void rejectsWhenCallerHasNoStaffProfile() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "teacher-portal-no-staff"));
		TenantContext.set(organisation.getId(), null);
		Person person = personRepository.save(new Person("No", "Staff"));
		User user = userRepository.save(new User("no-staff@example.com", null, "hash"));
		Role role = roleRepository.save(new Role("Teacher", "portal access", false, false, true));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));

		assertThatThrownBy(() -> teacherPortalService.leaveRequestsFor(user.getId())).isInstanceOf(AuthorizationDeniedException.class);
	}
}
