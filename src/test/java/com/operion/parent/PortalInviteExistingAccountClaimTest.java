package com.operion.parent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.MembershipService;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Permission;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.RoleRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.identity.auth.JwtService;
import com.operion.identity.auth.LoginResult;
import com.operion.identity.auth.RefreshTokenRepository;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.DepartmentRepository;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Guardian invite claim must not hand out a session for an account the claimer has not proved they own. A guardian's
 * email can be set by a GUARDIAN_MANAGE holder, so pointing it at the Owner's login and claiming the invite used to
 * return the Owner's session. The existing account's password must be supplied, is never overwritten, and every
 * failure is the same generic error.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PortalInviteExistingAccountClaimTest {

	private static final String TEST_SECRET = "test-only-secret-not-for-real-use-but-still-32-bytes-min";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
	private static final String REAL_PASSWORD = "OwnersRealPass1!";

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private PersonRepository personRepository;
	@Autowired
	private GuardianRepository guardianRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RoleRepository roleRepository;
	@Autowired
	private PermissionRepository permissionRepository;
	@Autowired
	private CampusRepository campusRepository;
	@Autowired
	private DepartmentRepository departmentRepository;
	@Autowired
	private OrganisationMembershipRepository membershipRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;
	@Autowired
	private PortalInviteRepository portalInviteRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private PortalInviteService portalInviteService() {
		MembershipService membershipService = new MembershipService(membershipRepository, userRepository, personRepository,
				roleRepository, campusRepository, departmentRepository, new AuditLogService(auditLogRepository, new ObjectMapper()));
		return new PortalInviteService(portalInviteRepository, guardianRepository, userRepository, roleRepository,
				permissionRepository, organisationRepository, membershipService, ENCODER, new JwtService(TEST_SECRET, 480),
				new RefreshTokenService(refreshTokenRepository, ENCODER));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(Organisation organisation, User existingUser, String rawToken, Long inviteId) {
	}

	/** An org with an account at {@code email} and a guardian invite pointed at that same email. */
	private Fixture fixture(String slugPrefix, UserStatus status) {
		permissionRepository.findByCode("PARENT_PORTAL_ACCESS")
				.orElseGet(() -> permissionRepository.save(new Permission("PARENT_PORTAL_ACCESS", "parent", null)));
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		String email = slugPrefix + "@takeover.test";
		User existing = new User(email, null, ENCODER.encode(REAL_PASSWORD));
		existing.setStatus(status);
		existing = userRepository.save(existing);
		Person person = personRepository.save(new Person("Target", "Guardian"));
		person.setEmail(email);
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());
		TenantContext.clear();
		return new Fixture(organisation, existing, issued.rawToken(), issued.inviteId());
	}

	private void assertNoSessionOrMembershipAndPasswordIntact(Fixture f) {
		TenantContext.set(f.organisation().getId(), null);
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(f.existingUser().getId())).isEmpty();
		assertThat(membershipRepository.findByUserId(f.existingUser().getId())).isEmpty();
		assertThat(portalInviteRepository.findById(f.inviteId()).orElseThrow().getStatus()).isEqualTo(PortalInviteStatus.PENDING);
		User reloaded = userRepository.findById(f.existingUser().getId()).orElseThrow();
		assertThat(ENCODER.matches(REAL_PASSWORD, reloaded.getPasswordHash())).isTrue();
		TenantContext.clear();
	}

	@Test
	void claimingForAnExistingAccountWithAWrongPasswordIsRejectedWithNoSession() {
		Fixture f = fixture("wrong-pw", UserStatus.ACTIVE);

		assertThatThrownBy(() -> portalInviteService().claim(f.organisation().getSlug(), f.rawToken(), "NotTheirPassword1!"))
				.isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid or expired invite");

		assertNoSessionOrMembershipAndPasswordIntact(f);
	}

	@Test
	void claimingForAnExistingAccountWithNoPasswordIsRejectedWithNoSession() {
		Fixture f = fixture("no-pw", UserStatus.ACTIVE);

		assertThatThrownBy(() -> portalInviteService().claim(f.organisation().getSlug(), f.rawToken(), null))
				.isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid or expired invite");

		assertNoSessionOrMembershipAndPasswordIntact(f);
	}

	@Test
	void claimingForAnExistingAccountWithItsRealPasswordStillWorksAndKeepsThatPassword() {
		Fixture f = fixture("right-pw", UserStatus.ACTIVE);

		LoginResult result = portalInviteService().claim(f.organisation().getSlug(), f.rawToken(), REAL_PASSWORD);

		assertThat(result.userId()).isEqualTo(f.existingUser().getId());
		assertThat(result.token()).isNotBlank();
		TenantContext.set(f.organisation().getId(), null);
		assertThat(membershipRepository.findByUserId(f.existingUser().getId())).hasSize(1);
		assertThat(portalInviteRepository.findById(f.inviteId()).orElseThrow().getStatus()).isEqualTo(PortalInviteStatus.CLAIMED);
		User reloaded = userRepository.findById(f.existingUser().getId()).orElseThrow();
		assertThat(ENCODER.matches(REAL_PASSWORD, reloaded.getPasswordHash())).isTrue();
	}

	@Test
	void claimingForADisabledExistingAccountIsRejectedEvenWithItsRealPassword() {
		Fixture f = fixture("disabled", UserStatus.DISABLED);

		assertThatThrownBy(() -> portalInviteService().claim(f.organisation().getSlug(), f.rawToken(), REAL_PASSWORD))
				.isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid or expired invite");

		assertNoSessionOrMembershipAndPasswordIntact(f);
	}

	@Test
	void repeatedWrongPasswordClaimsAgainstOneAccountAreThrottledEvenForTheRightPassword() {
		Fixture f = fixture("throttle", UserStatus.ACTIVE);
		PortalInviteService service = portalInviteService();

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.claim(f.organisation().getSlug(), f.rawToken(), "guess-" + System.nanoTime()))
					.isInstanceOf(AuthenticationFailedException.class);
		}

		// The sixth attempt is refused outright, so a brute-forcer cannot use the claim endpoint as a password oracle.
		assertThatThrownBy(() -> service.claim(f.organisation().getSlug(), f.rawToken(), REAL_PASSWORD))
				.isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid or expired invite");
		assertNoSessionOrMembershipAndPasswordIntact(f);
	}
}
