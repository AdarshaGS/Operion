package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MemberStatus;
import com.operion.authorization.MembershipService;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionChecker;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.authorization.RoleStatus;
import com.operion.calendar.WorkingDayService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.email.EmailOutboxRepository;
import com.operion.hr.api.StaffAccessRequest;
import com.operion.hr.api.StaffAccessSummary;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.identity.auth.EmailVerificationTokenRepository;
import com.operion.identity.auth.StaffInvite;
import com.operion.identity.auth.StaffInviteStatus;
import com.operion.identity.auth.RefreshTokenRepository;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.identity.auth.StaffInviteRepository;
import com.operion.identity.auth.StaffInviteService;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.JwtService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * The optional Staff -> Members link: giving a staff member a login (at creation or later), reading access status,
 * the HR-profile lookup behind the Members list's flag, and the exit that withdraws a login.
 *
 * Services are built by hand (as in StaffLifecycleTest) and StaffAccessService is wrapped in the real transaction
 * interceptor, so the atomicity tests exercise an actual rollback rather than a mock of one.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffAccessTest {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired private WorkingCalendarEntryRepository workingCalendarEntryRepository;
	@Autowired private OrganisationConfigurationRepository organisationConfigurationRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;
	@Autowired private LeaveTypeRepository leaveTypeRepository;
	@Autowired private LeaveBalanceRepository leaveBalanceRepository;
	@Autowired private LeaveRequestRepository leaveRequestRepository;
	@Autowired private StaffDocumentRepository staffDocumentRepository;
	@Autowired private JobApplicationRepository jobApplicationRepository;
	@Autowired private StaffAssignmentRepository staffAssignmentRepository;
	@Autowired private StaffExitRepository staffExitRepository;
	@Autowired private StaffBankDetailRepository staffBankDetailRepository;
	@Autowired private StaffAttendanceRepository staffAttendanceRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private RoleRepository roleRepository;
	@Autowired private PermissionRepository permissionRepository;
	@Autowired private OrganisationMembershipRepository membershipRepository;
	@Autowired private StaffInviteRepository staffInviteRepository;
	@Autowired private RefreshTokenRepository refreshTokenRepository;
	@Autowired private EmailOutboxRepository emailOutboxRepository;
	@Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	private HrService hrService;
	private StaffAccessService staffAccessService;
	private StaffProfileLookup staffProfileLookup;
	private RefreshTokenService refreshTokenService;
	private StaffInviteService inviteService;
	private MembershipService membershipService;
	private EmailDeliveryService mail;

	private Organisation organisation;
	private Campus campus;
	private Designation designation;
	private Department department;
	private Role teacherRole;
	private Long actorId;

	@BeforeEach
	void setUp() {
		AuditLogService audit = new AuditLogService(auditLogRepository, new ObjectMapper());
		hrService = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new WorkingDayService(workingCalendarEntryRepository, organisationConfigurationRepository), audit);
		membershipService = new MembershipService(membershipRepository, userRepository, personRepository,
				roleRepository, campusRepository, departmentRepository, audit);
		mail = new EmailDeliveryService(List.of(), emailOutboxRepository);
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, ENCODER);
		inviteService = new StaffInviteService(staffInviteRepository, userRepository, membershipRepository, organisationRepository,
				ENCODER, new JwtService("test-only-secret-not-for-real-use-but-still-32-bytes-min", 480), refreshTokenService, mail,
				"http://localhost:5173");
		EmailVerificationService verification = new EmailVerificationService(emailVerificationTokenRepository, userRepository,
				organisationRepository, mail, "http://localhost:5173", false);
		staffAccessService = transactional(accessService(verification));
		staffProfileLookup = new StaffProfileLookup(staffProfileRepository);

		organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "staff-access-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		designation = designationRepository.save(new Designation("Teacher"));
		department = departmentRepository.save(new Department("Science"));
		Role teacher = new Role("Teacher", "teaches", false);
		teacher.grant(permission("SA_TEACH"));
		teacherRole = roleRepository.save(teacher);
		actorId = userWithMembership("hr-admin", "MEMBERSHIP_MANAGE", "SA_TEACH");
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private StaffAccessService accessService(EmailVerificationService verification) {
		return new StaffAccessService(hrService, membershipService, membershipRepository, inviteService, verification,
				refreshTokenService, userRepository, campusRepository, new PermissionChecker(membershipRepository));
	}

	/** Raised by the stand-in mail step below; distinct so a test can assert the failure it caused and not just "something threw". */
	private static final class SimulatedMailFailure extends RuntimeException {
		SimulatedMailFailure() {
			super("simulated failure after the login was written");
		}
	}

	private <T> T transactional(T service) {
		ProxyFactory proxyFactory = new ProxyFactory(service);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
		@SuppressWarnings("unchecked")
		T proxy = (T) proxyFactory.getProxy();
		return proxy;
	}

	private Permission permission(String code) {
		return permissionRepository.findAll().stream().filter(p -> p.getCode().equals(code)).findFirst()
				.orElseGet(() -> permissionRepository.save(new Permission(code, "test", null)));
	}

	/** A signed-in actor holding exactly {@code codes}. */
	private Long userWithMembership(String label, String... codes) {
		Role role = new Role(label + " " + System.nanoTime(), "test", false);
		for (String code : codes) {
			role.grant(permission(code));
		}
		role = roleRepository.save(role);
		User user = userRepository.save(new User(label + "-" + System.nanoTime() + "@staff-access.test", null, "hash"));
		membershipRepository.save(new OrganisationMembership(user, personRepository.save(new Person(label, "Actor")), role, null));
		return user.getId();
	}

	private Person newPerson(String first) {
		return personRepository.save(new Person(first, "Staff"));
	}

	private StaffProfile plainStaff(Person person, String code) {
		return hrService.createStaffProfile(person, campus, code, designation, department, LocalDate.of(2024, 6, 1), EmploymentType.PERMANENT);
	}

	private StaffAccessRequest access(String email) {
		return new StaffAccessRequest(email, teacherRole.getId(), null, null);
	}

	private StaffAccessService.AccessResult createWithAccess(Person person, String code, StaffAccessRequest access, Long actor) {
		return createWithAccess(person, code, null, access, actor);
	}

	private StaffAccessService.AccessResult createWithAccess(Person person, String code, Long reportingManagerId, StaffAccessRequest access,
			Long actor) {
		return staffAccessService.createStaffWithAccess(person, campus, code, designation, department, LocalDate.of(2024, 6, 1),
				EmploymentType.PERMANENT, reportingManagerId, access, actor);
	}

	/** A login (ACTIVE unless {@code status} says otherwise) holding a membership of {@code role} on {@code person}. */
	private User memberWithRole(String label, Person person, Role role, UserStatus status) {
		User user = new User(label + "-" + System.nanoTime() + "@staff-access.test", null, "hash");
		user.setStatus(status);
		user = userRepository.save(user);
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
		return user;
	}

	private Role adminRole() {
		return roleRepository.save(new Role("Org Admin " + System.nanoTime(), "admin", true));
	}

	@Test
	void creatingStaffWithAccessCreatesProfileInvitedLoginAndMembershipLinkedToThePerson() {
		Person person = newPerson("Asha");

		StaffAccessService.AccessResult result = createWithAccess(person, "EMP-A1", access("asha@staff-access.test"), actorId);

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isPresent();
		User user = userRepository.findByEmail("asha@staff-access.test").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(result.invite().rawToken()).isNotBlank();
		assertThat(result.invite().userId()).isEqualTo(user.getId());
		assertThat(emailVerificationTokenRepository.findAll()).isNotEmpty();
		List<OrganisationMembership> memberships = membershipRepository.findByPersonId(person.getId());
		assertThat(memberships).hasSize(1);
		OrganisationMembership membership = memberships.get(0);
		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getUser().getId()).isEqualTo(user.getId());
		assertThat(membership.getRole().getId()).isEqualTo(teacherRole.getId());
		assertThat(membership.getCampus().getId()).isEqualTo(campus.getId());
		assertThat(membership.getDepartment().getId()).isEqualTo(department.getId());
		assertThat(membership.getJoiningDate()).isEqualTo(LocalDate.of(2024, 6, 1));
	}

	@Test
	void theAccessBlocksCampusOverridesTheStaffMembersOwn() {
		Campus other = campusRepository.save(new Campus("North Campus", "NORTH"));
		Person person = newPerson("Bala");

		createWithAccess(person, "EMP-B1", new StaffAccessRequest("bala@staff-access.test", teacherRole.getId(), other.getId(), "9000000001"), actorId);

		OrganisationMembership membership = membershipRepository.findByPersonId(person.getId()).get(0);
		assertThat(membership.getCampus().getId()).isEqualTo(other.getId());
		assertThat(userRepository.findByEmail("bala@staff-access.test").orElseThrow().getPhone()).isEqualTo("9000000001");
	}

	@Test
	void aRoleTheActorCannotDelegateLeavesNoStaffProfileBehind() {
		Role admin = new Role("Super " + System.nanoTime(), "more than the actor holds", false);
		admin.grant(permission("SA_ONLY_ADMIN_HAS"));
		Long adminRoleId = roleRepository.save(admin).getId();
		Person person = newPerson("Chitra");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-C1", new StaffAccessRequest("chitra@staff-access.test", adminRoleId, null, null), actorId))
				.isInstanceOf(AuthorizationDeniedException.class);

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(userRepository.findByEmail("chitra@staff-access.test")).isEmpty();
	}

	@Test
	void anInactiveOrUnknownRoleIsRefusedWithNothingCreated() {
		Role retired = new Role("Retired " + System.nanoTime(), "gone", false);
		retired.setStatus(RoleStatus.INACTIVE);
		Long retiredId = roleRepository.save(retired).getId();
		Person person = newPerson("Dev");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-D1", new StaffAccessRequest("dev@staff-access.test", retiredId, null, null), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive");
		assertThatThrownBy(() -> createWithAccess(person, "EMP-D1", new StaffAccessRequest("dev@staff-access.test", 999_999L, null, null), actorId))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(userRepository.findByEmail("dev@staff-access.test")).isEmpty();
	}

	@Test
	void anActorWithoutMembershipManageCannotCreateAccessAndNothingIsWritten() {
		Long hrOnly = userWithMembership("hr-only", "HR_STAFF_MANAGE", "SA_TEACH");
		Person person = newPerson("Esha");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-E1", access("esha@staff-access.test"), hrOnly))
				.isInstanceOf(AuthorizationDeniedException.class);

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(userRepository.findByEmail("esha@staff-access.test")).isEmpty();
	}

	@Test
	void aDuplicateEmailIsRefusedAndLeavesNoStaffProfile() {
		userRepository.save(new User("taken@staff-access.test", null, "hash"));
		Person person = newPerson("Farah");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-F1", access("taken@staff-access.test"), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
	}

	@Test
	void aFailureAfterTheProfileIsWrittenRollsTheProfileBackToo() {
		// The mail step runs after the staff profile, the login shell and its invite are all inserted - a failure there must undo them.
		EmailVerificationService failing = new EmailVerificationService(emailVerificationTokenRepository, userRepository,
				organisationRepository, mail, "http://localhost:5173", false) {
			@Override
			public void issue(Long userId) {
				throw new SimulatedMailFailure();
			}
		};
		StaffAccessService failingAccess = transactional(accessService(failing));
		Person person = newPerson("Gita");

		assertThatThrownBy(() -> failingAccess.createStaffWithAccess(person, campus, "EMP-G1", designation, department,
				LocalDate.of(2024, 6, 1), EmploymentType.PERMANENT, null, access("gita@staff-access.test"), actorId))
				.isInstanceOf(SimulatedMailFailure.class);

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(staffProfileRepository.findByEmployeeCode("EMP-G1")).isEmpty();
		assertThat(userRepository.findByEmail("gita@staff-access.test")).isEmpty();
		assertThat(membershipRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(staffInviteRepository.findAll()).isEmpty();
	}

	@Test
	void aDuplicatePhoneIsRefusedUpFrontWithASpecificMessageAndNothingIsWritten() {
		userRepository.save(new User("phone-owner@staff-access.test", "9111111111", "hash"));
		Person person = newPerson("Gauri");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-G2",
				new StaffAccessRequest("gauri@staff-access.test", teacherRole.getId(), null, "9111111111"), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("phone number 9111111111");

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(userRepository.findByEmail("gauri@staff-access.test")).isEmpty();
		assertThat(staffInviteRepository.findAll()).isEmpty();
	}

	@Test
	void givingAccessWithADuplicatePhoneIsRefusedWithTheSameMessage() {
		userRepository.save(new User("phone-owner2@staff-access.test", "9222222222", "hash"));
		StaffProfile staff = plainStaff(newPerson("Geeta"), "EMP-G3");

		assertThatThrownBy(() -> staffAccessService.giveAccess(staff,
				new StaffAccessRequest("geeta@staff-access.test", teacherRole.getId(), null, " 9222222222 "), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("phone number 9222222222");

		assertThat(userRepository.findByEmail("geeta@staff-access.test")).isEmpty();
		assertThat(membershipRepository.findByPersonId(staff.getPerson().getId())).isEmpty();
	}

	@Test
	void aDuplicateEmployeeCodeIsRefusedWithASpecificMessageAndNoLoginIsCreated() {
		plainStaff(newPerson("Hema"), "EMP-DUP");
		Person person = newPerson("Ishaan");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-DUP", access("ishaan@staff-access.test"), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("employee code EMP-DUP");

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(userRepository.findByEmail("ishaan@staff-access.test")).isEmpty();
	}

	@Test
	void anUnknownReportingManagerLeavesNoProfileNoLoginAndNoMembership() {
		Person person = newPerson("Jiya");

		assertThatThrownBy(() -> createWithAccess(person, "EMP-RM1", 999_999L, access("jiya@staff-access.test"), actorId))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No staff profile with id 999999");

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(staffProfileRepository.findByEmployeeCode("EMP-RM1")).isEmpty();
		assertThat(userRepository.findByEmail("jiya@staff-access.test")).isEmpty();
		assertThat(membershipRepository.findByPersonId(person.getId())).isEmpty();
	}

	@Test
	void anUnknownReportingManagerOnAPlainCreateLeavesNoProfile() {
		Person person = newPerson("Kabir");
		long assignmentsBefore = staffAssignmentRepository.count();

		assertThatThrownBy(() -> hrService.createStaffProfile(person, campus, "EMP-RM2", designation, department,
				LocalDate.of(2024, 6, 1), EmploymentType.PERMANENT, 999_999L))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No staff profile with id");

		assertThat(staffProfileRepository.findByPersonId(person.getId())).isEmpty();
		assertThat(staffProfileRepository.findByEmployeeCode("EMP-RM2")).isEmpty();
		assertThat(staffAssignmentRepository.count()).isEqualTo(assignmentsBefore);
	}

	@Test
	void aKnownReportingManagerIsStoredWithTheProfile() {
		StaffProfile manager = plainStaff(newPerson("Leela"), "EMP-RM3");
		Person person = newPerson("Mohan");

		StaffAccessService.AccessResult result = createWithAccess(person, "EMP-RM4", manager.getId(), access("mohan@staff-access.test"), actorId);

		StaffProfile saved = staffProfileRepository.findById(result.staffProfile().getId()).orElseThrow();
		assertThat(saved.getReportingManager()).isNotNull();
		assertThat(saved.getReportingManager().getId()).isEqualTo(manager.getId());
	}

	@Test
	void givingAccessToExistingStaffWorksOnceAndASecondAttemptConflicts() {
		Person person = newPerson("Hari");
		StaffProfile staff = plainStaff(person, "EMP-H1");
		assertThat(membershipRepository.findByPersonId(person.getId())).isEmpty();

		StaffAccessService.AccessResult result = staffAccessService.giveAccess(staff, access("hari@staff-access.test"), actorId);

		assertThat(result.invite().rawToken()).isNotBlank();
		assertThat(membershipRepository.findByPersonId(person.getId())).hasSize(1);
		assertThatThrownBy(() -> staffAccessService.giveAccess(staff, access("hari2@staff-access.test"), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already has login access");
		assertThat(userRepository.findByEmail("hari2@staff-access.test")).isEmpty();
	}

	@Test
	void givingAccessToStaffWhoHaveLeftConflicts() {
		Person person = newPerson("Indu");
		StaffProfile staff = plainStaff(person, "EMP-I1");
		hrService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 1, 1), "moved", actorId);
		StaffProfile left = staffProfileRepository.findById(staff.getId()).orElseThrow();

		assertThatThrownBy(() -> staffAccessService.giveAccess(left, access("indu@staff-access.test"), actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("has left");
		assertThat(userRepository.findByEmail("indu@staff-access.test")).isEmpty();
	}

	@Test
	void givingAccessNeedsMembershipManageAsWellAsTheRoleBeingHeld() {
		Long hrOnly = userWithMembership("hr-only2", "HR_STAFF_MANAGE", "SA_TEACH");
		StaffProfile staff = plainStaff(newPerson("Jaya"), "EMP-J1");

		assertThatThrownBy(() -> staffAccessService.giveAccess(staff, access("jaya@staff-access.test"), hrOnly))
				.isInstanceOf(AuthorizationDeniedException.class);
		assertThat(userRepository.findByEmail("jaya@staff-access.test")).isEmpty();
	}

	@Test
	void accessSummaryReportsNoneInvitedAndActiveAndRevoked() {
		Person without = newPerson("Kiran");
		Person invited = newPerson("Lata");
		Person revoked = newPerson("Manu");
		plainStaff(without, "EMP-K1");
		createWithAccess(invited, "EMP-L1", access("lata@staff-access.test"), actorId);
		createWithAccess(revoked, "EMP-M1", access("manu@staff-access.test"), actorId);
		OrganisationMembership toRevoke = membershipRepository.findByPersonId(revoked.getId()).get(0);
		toRevoke.setStatus(MembershipStatus.INACTIVE);
		membershipRepository.save(toRevoke);

		Map<Long, StaffAccessSummary> summaries = staffAccessService.summariseAll(List.of(without.getId(), invited.getId(), revoked.getId()));

		assertThat(summaries).doesNotContainKey(without.getId());
		assertThat(staffAccessService.summarise(without.getId())).isEqualTo(StaffAccessSummary.NONE);
		StaffAccessSummary invitedSummary = summaries.get(invited.getId());
		assertThat(invitedSummary.hasAccess()).isTrue();
		assertThat(invitedSummary.status()).isEqualTo(MemberStatus.INVITED.name());
		assertThat(invitedSummary.roleName()).isEqualTo("Teacher");
		assertThat(invitedSummary.membershipId()).isNotNull();
		StaffAccessSummary revokedSummary = summaries.get(revoked.getId());
		assertThat(revokedSummary.hasAccess()).isFalse();
		assertThat(revokedSummary.status()).isEqualTo(MemberStatus.INACTIVE.name());
		assertThat(invitedSummary.withoutDetail().membershipId()).isNull();
		assertThat(invitedSummary.withoutDetail().roleName()).isNull();
		assertThat(invitedSummary.withoutDetail().hasAccess()).isTrue();
	}

	@Test
	void theHrProfileLookupSeesEveryStaffPersonIncludingThoseWhoLeftAndNobodyElse() {
		Person current = newPerson("Nita");
		Person gone = newPerson("Om");
		Person nonStaff = newPerson("Pia");
		plainStaff(current, "EMP-N1");
		StaffProfile leaver = plainStaff(gone, "EMP-O1");
		hrService.recordExit(leaver, StaffExitType.RESIGNATION, LocalDate.of(2025, 1, 1), "moved", actorId);

		assertThat(staffProfileLookup.personIdsWithProfile()).contains(current.getId(), gone.getId()).doesNotContain(nonStaff.getId());
		assertThat(staffProfileLookup.hasProfile(current.getId())).isTrue();
		assertThat(staffProfileLookup.hasProfile(nonStaff.getId())).isFalse();
	}

	@Test
	void recordingAnExitRevokesTheLoginAndItsRefreshTokens() {
		Person person = newPerson("Qadir");
		StaffAccessService.AccessResult created = createWithAccess(person, "EMP-Q1", access("qadir@staff-access.test"), actorId);
		Long userId = created.invite().userId();
		refreshTokenService.issue(userId);
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).hasSize(1);

		StaffExit exit = staffAccessService.recordExit(created.staffProfile(), StaffExitType.TERMINATION, LocalDate.of(2025, 2, 1), "x", actorId);

		assertThat(exit.getId()).isNotNull();
		assertThat(staffProfileRepository.findById(created.staffProfile().getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.TERMINATED);
		OrganisationMembership membership = membershipRepository.findByPersonId(person.getId()).get(0);
		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
		// Login, refresh and the per-request interceptor all require an ACTIVE membership - none is left.
		assertThat(membershipRepository.findByUserId(userId)).noneMatch(m -> m.getStatus() == MembershipStatus.ACTIVE);
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).isEmpty();
	}

	@Test
	void exitingStaffWithoutALoginBehavesAsBefore() {
		Person person = newPerson("Rina");
		StaffProfile staff = plainStaff(person, "EMP-R1");

		staffAccessService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "moved", actorId);

		assertThat(staffProfileRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.RESIGNED);
		assertThat(staffExitRepository.findByStaffProfileId(staff.getId())).hasSize(1);
	}

	@Test
	void exitingTheOwnerIsRefusedAndNothingIsRecorded() {
		Person person = newPerson("Sana");
		StaffProfile staff = plainStaff(person, "EMP-S1");
		Role ownerRole = roleRepository.save(new Role("Owner " + System.nanoTime(), "owner", false));
		User ownerUser = userRepository.save(new User("owner@staff-access.test", null, "hash"));
		membershipRepository.save(new OrganisationMembership(ownerUser, person, ownerRole, null, null, true));

		assertThatThrownBy(() -> staffAccessService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "x", actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Owner");

		assertThat(staffProfileRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ACTIVE);
		assertThat(staffExitRepository.findByStaffProfileId(staff.getId())).isEmpty();
		assertThat(membershipRepository.findByPersonId(person.getId())).allMatch(m -> m.getStatus() == MembershipStatus.ACTIVE);
	}

	@Test
	void anExitThatCannotRevokeTheLastOrgAdminIsRefusedWholesale() {
		Person person = newPerson("Tara");
		StaffProfile staff = plainStaff(person, "EMP-T1");
		Role admin = roleRepository.save(new Role("Org Admin " + System.nanoTime(), "admin", true));
		User adminUser = userRepository.save(new User("admin@staff-access.test", null, "hash"));
		membershipRepository.save(new OrganisationMembership(adminUser, person, admin, null));

		assertThatThrownBy(() -> staffAccessService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "x", actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("last active Org Admin");

		assertThat(staffProfileRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ACTIVE);
		assertThat(staffExitRepository.findByStaffProfileId(staff.getId())).isEmpty();
	}

	@Test
	void exitingStaffWithAPendingInviteRevokesTheInviteAndDisablesTheUnclaimedLogin() {
		Person person = newPerson("Uma");
		StaffAccessService.AccessResult created = createWithAccess(person, "EMP-U1", access("uma@staff-access.test"), actorId);
		Long userId = created.invite().userId();
		assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(staffInviteRepository.findByUserIdAndStatus(userId, StaffInviteStatus.PENDING)).hasSize(1);

		staffAccessService.recordExit(created.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "moved", actorId);

		assertThat(staffInviteRepository.findByUserIdAndStatus(userId, StaffInviteStatus.PENDING)).isEmpty();
		assertThat(staffInviteRepository.findAll()).filteredOn(invite -> invite.getUserId().equals(userId))
				.allMatch(invite -> invite.getStatus() == StaffInviteStatus.REVOKED);
		assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);
		// Last, because claim() clears the tenant context: the emailed link no longer works.
		assertThatThrownBy(() -> inviteService.claim(organisation.getSlug(), created.invite().rawToken(), "a-new-password"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void exitingStaffWhoseLoginKeepsAnotherActiveMembershipLeavesItsTokensAndStatusAlone() {
		Person person = newPerson("Vikram");
		StaffAccessService.AccessResult created = createWithAccess(person, "EMP-V1", access("vikram@staff-access.test"), actorId);
		Long userId = created.invite().userId();
		User user = userRepository.findById(userId).orElseThrow();
		user.setStatus(UserStatus.ACTIVE);
		userRepository.save(user);
		// The same login also holds a membership on a different person (e.g. a portal/guardian record), which stays ACTIVE.
		Role otherRole = roleRepository.save(new Role("Other " + System.nanoTime(), "other", false));
		membershipRepository.save(new OrganisationMembership(user, newPerson("Vikram-other"), otherRole, null));
		refreshTokenService.issue(userId);

		staffAccessService.recordExit(created.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "moved", actorId);

		assertThat(membershipRepository.findByPersonId(person.getId())).allMatch(m -> m.getStatus() == MembershipStatus.INACTIVE);
		assertThat(membershipRepository.findByUserId(userId)).filteredOn(m -> m.getStatus() == MembershipStatus.ACTIVE).hasSize(1);
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).hasSize(1);
		assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(staffInviteRepository.findByUserIdAndStatus(userId, StaffInviteStatus.PENDING)).hasSize(1);
	}

	@Test
	void anAdminCanExitWhenAnotherAdminWithAnActiveLoginRemains() {
		Person person = newPerson("Wasim");
		StaffProfile staff = plainStaff(person, "EMP-W1");
		Role admin = adminRole();
		User leaving = memberWithRole("leaving-admin", person, admin, UserStatus.ACTIVE);
		memberWithRole("staying-admin", newPerson("Xena"), admin, UserStatus.ACTIVE);

		staffAccessService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "moved", actorId);

		assertThat(staffProfileRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.RESIGNED);
		assertThat(membershipRepository.findByUserId(leaving.getId())).allMatch(m -> m.getStatus() == MembershipStatus.INACTIVE);
	}

	@Test
	void anAdminCannotExitWhenTheOnlyOtherAdminIsAPendingLoginAndNothingIsRecorded() {
		Person person = newPerson("Yash");
		StaffProfile staff = plainStaff(person, "EMP-Y1");
		Role admin = adminRole();
		User leaving = memberWithRole("leaving-admin2", person, admin, UserStatus.ACTIVE);
		memberWithRole("pending-admin", newPerson("Zoya"), admin, UserStatus.PENDING);

		assertThatThrownBy(() -> staffAccessService.recordExit(staff, StaffExitType.RESIGNATION, LocalDate.of(2025, 2, 1), "x", actorId))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("last active Org Admin");

		assertThat(staffProfileRepository.findById(staff.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ACTIVE);
		assertThat(staffExitRepository.findByStaffProfileId(staff.getId())).isEmpty();
		assertThat(membershipRepository.findByUserId(leaving.getId())).allMatch(m -> m.getStatus() == MembershipStatus.ACTIVE);
	}

	@Test
	void anActiveMembershipWinsOverALaterRevokedOneInTheAccessSummary() {
		Person person = newPerson("Anita");
		User user = userRepository.save(new User("anita@staff-access.test", null, "hash"));
		Role second = roleRepository.save(new Role("Second " + System.nanoTime(), "second", false));
		OrganisationMembership active = membershipRepository.save(new OrganisationMembership(user, person, teacherRole, null));
		OrganisationMembership laterRevoked = new OrganisationMembership(user, person, second, null);
		laterRevoked.setStatus(MembershipStatus.INACTIVE);
		laterRevoked = membershipRepository.save(laterRevoked);
		assertThat(laterRevoked.getId()).isGreaterThan(active.getId());

		StaffAccessSummary summary = staffAccessService.summarise(person.getId());

		assertThat(summary.hasAccess()).isTrue();
		assertThat(summary.membershipId()).isEqualTo(active.getId());
		assertThat(summary.roleName()).isEqualTo("Teacher");
		assertThat(summary.status()).isEqualTo(MemberStatus.ACTIVE.name());
	}
}
