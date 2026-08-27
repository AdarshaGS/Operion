package com.operion.parent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.MembershipService;
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
 * Proves PortalInviteService turns a Guardian's existing Person into a real login
 * without a parallel identity system (reuses User/OrganisationMembership/Role exactly
 * like staff onboarding), and that claim() is tenant-safe despite running before any
 * token exists to resolve TenantContext from - same shape as
 * AuthenticationService.login(), same reason it's hand-constructed here rather than
 * @Import'd (see MembershipServiceTest/PlatformAuthenticationServiceTest for the same
 * @DataJpaTest-slice-has-no-PasswordEncoder-or-JWT-secret-bean reasoning).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PortalInviteLifecycleTest {

	private static final String TEST_SECRET = "test-only-secret-not-for-real-use-but-still-32-bytes-min";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

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
				organisationRepository, membershipService, ENCODER, new JwtService(TEST_SECRET, 480),
				new RefreshTokenService(refreshTokenRepository, ENCODER));
	}

	private Organisation newOrgWithGuardianRole(String slugPrefix) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		roleRepository.save(new Role("Guardian", "Guardian (default)", false));
		return organisation;
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void issuingAnInviteNeverStoresTheRawToken() {
		newOrgWithGuardianRole("issue");
		Person person = personRepository.save(new Person("Priya", "Guardian"));
		person.setEmail("priya@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));

		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());

		PortalInvite stored = portalInviteRepository.findById(issued.inviteId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(PortalInviteStatus.PENDING);
		assertThat(stored.getTokenHash()).isNotEqualTo(issued.rawToken());
		assertThat(ENCODER.matches(issued.rawToken(), stored.getTokenHash())).isTrue();
	}

	@Test
	void claimingCreatesAUserAndAnActiveGuardianMembership() {
		Organisation organisation = newOrgWithGuardianRole("claim");
		Person person = personRepository.save(new Person("Rahul", "Guardian"));
		person.setEmail("rahul-claim@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());
		TenantContext.clear();

		LoginResult result = portalInviteService().claim(organisation.getSlug(), issued.rawToken(), "NewParentPass123!");

		assertThat(result.organisationId()).isEqualTo(organisation.getId());
		User createdUser = userRepository.findByEmail("rahul-claim@example.com").orElseThrow();
		assertThat(result.userId()).isEqualTo(createdUser.getId());
		assertThat(ENCODER.matches("NewParentPass123!", createdUser.getPasswordHash())).isTrue();

		TenantContext.set(organisation.getId(), null);
		assertThat(membershipRepository.findByUserId(createdUser.getId())).hasSize(1);
		assertThat(portalInviteRepository.findById(issued.inviteId()).orElseThrow().getStatus()).isEqualTo(PortalInviteStatus.CLAIMED);
	}

	@Test
	void claimingReusesAnExistingUserWithoutOverwritingItsPassword() {
		Organisation organisation = newOrgWithGuardianRole("reuse");
		User existingUser = userRepository.save(new User("shared@example.com", null, ENCODER.encode("OriginalPass1!")));
		Person person = personRepository.save(new Person("Shared", "Login"));
		person.setEmail("shared@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());
		TenantContext.clear();

		LoginResult result = portalInviteService().claim(organisation.getSlug(), issued.rawToken(), "AttemptedNewPass1!");

		assertThat(result.userId()).isEqualTo(existingUser.getId());
		User reloaded = userRepository.findById(existingUser.getId()).orElseThrow();
		assertThat(ENCODER.matches("OriginalPass1!", reloaded.getPasswordHash())).isTrue();
	}

	@Test
	void claimingRejectsAWrongToken() {
		Organisation organisation = newOrgWithGuardianRole("wrong-token");
		Person person = personRepository.save(new Person("Wrong", "Token"));
		person.setEmail("wrong-token@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		portalInviteService().issue(guardian.getId());
		TenantContext.clear();

		assertThatThrownBy(() -> portalInviteService().claim(organisation.getSlug(), "not-the-real-token", "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void claimingRejectsAnExpiredInvite() {
		Organisation organisation = newOrgWithGuardianRole("expired");
		Person person = personRepository.save(new Person("Expired", "Invite"));
		person.setEmail("expired@example.com");
		personRepository.save(person);
		String rawToken = "expired-raw-token";
		portalInviteRepository.save(new PortalInvite(person, ENCODER.encode(rawToken), Instant.now().minusSeconds(60)));
		TenantContext.clear();

		assertThatThrownBy(() -> portalInviteService().claim(organisation.getSlug(), rawToken, "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void claimingTwiceWithTheSameTokenFailsTheSecondTime() {
		Organisation organisation = newOrgWithGuardianRole("double-claim");
		Person person = personRepository.save(new Person("Double", "Claim"));
		person.setEmail("double-claim@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());
		TenantContext.clear();

		portalInviteService().claim(organisation.getSlug(), issued.rawToken(), "FirstClaimPass1!");

		assertThatThrownBy(() -> portalInviteService().claim(organisation.getSlug(), issued.rawToken(), "SecondClaimPass1!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void anInviteFromOneOrgCannotBeClaimedThroughAnotherOrgsSlug() {
		newOrgWithGuardianRole("tenant-a");
		Person person = personRepository.save(new Person("Cross", "Tenant"));
		person.setEmail("cross-tenant@example.com");
		personRepository.save(person);
		Guardian guardian = guardianRepository.save(new Guardian(person, null));
		PortalInviteService.IssuedInvite issued = portalInviteService().issue(guardian.getId());
		TenantContext.clear();

		Organisation orgB = newOrgWithGuardianRole("tenant-b");
		TenantContext.clear();

		assertThatThrownBy(() -> portalInviteService().claim(orgB.getSlug(), issued.rawToken(), "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}
}
