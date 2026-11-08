package com.operion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.operion.audit.AuditLogService;
import com.operion.authorization.MembershipService;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserService;
import com.operion.identity.UserStatus;
import com.operion.identity.auth.AuthenticationService;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.JwtService;
import com.operion.identity.auth.LoginAttemptRepository;
import com.operion.identity.auth.LoginAttemptService;
import com.operion.identity.auth.LoginResult;
import com.operion.identity.auth.RefreshTokenRepository;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.identity.auth.StaffInviteService;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members are created with an admin-set password, not an invite token: POST /api/v1/users
 * followed by a membership grant (the order web/src/components/AddMemberForm.tsx's
 * submitAddMember uses) must leave a login that works immediately. Drives the real
 * UserController.create(), MembershipService.grant() and AuthenticationService.login() over
 * the DataJpaTest repositories - the web layer itself has no MockMvc precedent here (see
 * UserVisibilityTest) - with only the email/audit side effects mocked.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateUserWithPasswordTest {

	private static final String TEST_SECRET = "test-only-secret-not-for-real-use-but-still-32-bytes-min";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private UserController userController() {
		return new UserController(userRepository, membershipRepository, ENCODER, mock(UserService.class),
				mock(StaffInviteService.class), emailVerificationService);
	}

	private AuthenticationService authenticationService() {
		return new AuthenticationService(organisationRepository, userRepository, membershipRepository, ENCODER,
				new JwtService(TEST_SECRET, 480), new RefreshTokenService(refreshTokenRepository, ENCODER),
				new LoginAttemptService(loginAttemptRepository, 5, 15));
	}

	@Test
	void aMemberCreatedWithAPasswordIsActiveHashedAndCanLogInImmediately() {
		Organisation organisation = organisationRepository
				.save(new Organisation("Create School", "Create School Trust", "create-user-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		String email = "new-member@create-user.test";
		String rawPassword = "AdminSetPassw0rd!";

		UserResponse created = userController().create(new CreateUserRequest(email, null, rawPassword));

		// ACTIVE straight away - not PENDING, so no claim/invite step stands between the member and login.
		assertThat(created.status()).isEqualTo(UserStatus.ACTIVE.name());
		User stored = userRepository.findById(created.id()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(stored.getPasswordHash()).isNotEqualTo(rawPassword);
		assertThat(ENCODER.matches(rawPassword, stored.getPasswordHash())).isTrue();
		verify(emailVerificationService).issue(created.id());

		// Then the membership grant the Add-member form performs next, per selected role.
		Person person = personRepository.save(new Person("New", "Member"));
		Role role = roleRepository.save(new Role("Create Role", "desc", false));
		MembershipService membershipService = new MembershipService(membershipRepository, userRepository, personRepository,
				roleRepository, campusRepository, departmentRepository, mock(AuditLogService.class));
		OrganisationMembership membership = membershipService.grant(created.id(), person.getId(), role.getId(), null, null);
		assertThat(membership.getStatus().name()).isEqualTo("ACTIVE");

		// Through the real login path, with no email verification and no invite claim in between.
		TenantContext.clear();
		LoginResult result = authenticationService().login(organisation.getSlug(), email, rawPassword);
		assertThat(result.userId()).isEqualTo(created.id());
		assertThat(result.organisationId()).isEqualTo(organisation.getId());
	}

	@Test
	void thePasswordMustBePresentAndAtLeastEightCharacters() {
		assertThat(VALIDATOR.validate(new CreateUserRequest("a@create-user.test", null, "short"))).hasSize(1);
		assertThat(VALIDATOR.validate(new CreateUserRequest("a@create-user.test", null, ""))).isNotEmpty();
		assertThat(VALIDATOR.validate(new CreateUserRequest("a@create-user.test", null, null))).isNotEmpty();
		assertThat(VALIDATOR.validate(new CreateUserRequest("a@create-user.test", null, "longenough"))).isEmpty();
	}
}
