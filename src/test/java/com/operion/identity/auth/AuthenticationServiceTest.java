package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.operion.identity.UserStatus;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Covers #40 (UserStatus enforcement), #46 (brute-force lockout) and #43 (self-service
 * password change) on AuthenticationService - hand-constructed for the same
 * DataJpaTest-slice-has-no-PasswordEncoder-or-JWT-secret-bean reasoning as
 * PortalInviteLifecycleTest. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthenticationServiceTest {

	private static final String TEST_SECRET = "test-only-secret-not-for-real-use-but-still-32-bytes-min";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

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
	private RefreshTokenRepository refreshTokenRepository;

	private AuthenticationService authenticationService(LoginAttemptService loginAttemptService) {
		return new AuthenticationService(organisationRepository, userRepository, membershipRepository, ENCODER,
				new JwtService(TEST_SECRET, 480), new RefreshTokenService(refreshTokenRepository, ENCODER), loginAttemptService);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation newOrgWithMember(String slugPrefix, String email, String rawPassword, UserStatus status) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User(email, null, ENCODER.encode(rawPassword)));
		user.setStatus(status);
		userRepository.save(user);
		Person person = personRepository.save(new Person("Test", "User"));
		Role role = roleRepository.save(new Role("Some Role", "desc", false));
		membershipRepository.save(new OrganisationMembership(user, person, role, null));
		return organisation;
	}

	@Test
	void aLockedUserCannotLogIn() {
		Organisation organisation = newOrgWithMember("locked", "locked@auth-svc.test", "GoodPass123!", UserStatus.LOCKED);
		TenantContext.clear();

		assertThatThrownBy(() -> authenticationService(new LoginAttemptService(5, 15))
				.login(organisation.getSlug(), "locked@auth-svc.test", "GoodPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void aDisabledUserCannotLogIn() {
		Organisation organisation = newOrgWithMember("disabled", "disabled@auth-svc.test", "GoodPass123!", UserStatus.DISABLED);
		TenantContext.clear();

		assertThatThrownBy(() -> authenticationService(new LoginAttemptService(5, 15))
				.login(organisation.getSlug(), "disabled@auth-svc.test", "GoodPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void anActiveUserCanStillLogIn() {
		Organisation organisation = newOrgWithMember("active", "active@auth-svc.test", "GoodPass123!", UserStatus.ACTIVE);
		TenantContext.clear();

		LoginResult result = authenticationService(new LoginAttemptService(5, 15))
				.login(organisation.getSlug(), "active@auth-svc.test", "GoodPass123!");

		assertThat(result.organisationId()).isEqualTo(organisation.getId());
	}

	@Test
	void repeatedFailedAttemptsLockTheAccountOutEvenForAFinallyCorrectPassword() {
		Organisation organisation = newOrgWithMember("brute", "brute@auth-svc.test", "GoodPass123!", UserStatus.ACTIVE);
		TenantContext.clear();
		AuthenticationService service = authenticationService(new LoginAttemptService(3, 15));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> service.login(organisation.getSlug(), "brute@auth-svc.test", "WrongPass!"))
					.isInstanceOf(AuthenticationFailedException.class);
		}

		assertThatThrownBy(() -> service.login(organisation.getSlug(), "brute@auth-svc.test", "GoodPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void aSuccessfulLoginClearsAnyPriorFailedAttemptCount() {
		Organisation organisation = newOrgWithMember("reset", "reset@auth-svc.test", "GoodPass123!", UserStatus.ACTIVE);
		TenantContext.clear();
		AuthenticationService service = authenticationService(new LoginAttemptService(3, 15));

		assertThatThrownBy(() -> service.login(organisation.getSlug(), "reset@auth-svc.test", "WrongPass!"))
				.isInstanceOf(AuthenticationFailedException.class);
		assertThatThrownBy(() -> service.login(organisation.getSlug(), "reset@auth-svc.test", "WrongPass!"))
				.isInstanceOf(AuthenticationFailedException.class);

		// Third attempt (of a 3-max lockout) succeeds - never reaches the lockout threshold.
		assertThat(service.login(organisation.getSlug(), "reset@auth-svc.test", "GoodPass123!").userId()).isNotNull();

		// A wrong attempt right after a success shouldn't count as "2 of 3" - proves the
		// counter was actually cleared, not just not-yet-tripped.
		assertThatThrownBy(() -> service.login(organisation.getSlug(), "reset@auth-svc.test", "WrongPass!"))
				.isInstanceOf(AuthenticationFailedException.class);
		assertThat(service.login(organisation.getSlug(), "reset@auth-svc.test", "GoodPass123!").userId()).isNotNull();
	}

	@Test
	void changePasswordUpdatesTheHashOnlyWhenTheCurrentPasswordMatches() {
		newOrgWithMember("change-pw", "change-pw@auth-svc.test", "OldPass123!", UserStatus.ACTIVE);
		User user = userRepository.findByEmail("change-pw@auth-svc.test").orElseThrow();
		AuthenticationService service = authenticationService(new LoginAttemptService(5, 15));

		// IllegalStateException (-> 409), not AuthenticationFailedException (-> 401) - see
		// changePassword()'s own javadoc for why a 401 here would be a real client-side bug.
		assertThatThrownBy(() -> service.changePassword(user.getId(), "WrongCurrent!", "NewPass123!"))
				.isInstanceOf(IllegalStateException.class);

		service.changePassword(user.getId(), "OldPass123!", "NewPass123!");
		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(ENCODER.matches("NewPass123!", reloaded.getPasswordHash())).isTrue();
	}
}
