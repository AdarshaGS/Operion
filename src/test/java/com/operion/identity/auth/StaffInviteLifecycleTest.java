package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
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

/** Covers #47 - staff invite (set-your-own-password). Mirrors PortalInviteLifecycleTest's
 * cases; the one behaviour unique here is that issue() already creates the User "login
 * shell" (PENDING, unusable placeholder password) rather than deferring User creation to
 * claim() the way the guardian flow does. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffInviteLifecycleTest {

	private static final String TEST_SECRET = "test-only-secret-not-for-real-use-but-still-32-bytes-min";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StaffInviteRepository staffInviteRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private StaffInviteService staffInviteService() {
		return new StaffInviteService(staffInviteRepository, userRepository, organisationRepository, ENCODER,
				new JwtService(TEST_SECRET, 480), new RefreshTokenService(refreshTokenRepository, ENCODER));
	}

	private Organisation newOrg(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void issuingCreatesAPendingUserAndNeverStoresTheRawToken() {
		Organisation organisation = newOrg("issue");
		TenantContext.set(organisation.getId(), null);

		StaffInviteService.IssuedInvite issued = staffInviteService().issue("new-hire@staff-invite.test", "9999999999");

		User user = userRepository.findByEmail("new-hire@staff-invite.test").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(ENCODER.matches("whatever-a-real-password-might-be", user.getPasswordHash())).isFalse();

		StaffInvite stored = staffInviteRepository.findById(issued.inviteId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(StaffInviteStatus.PENDING);
		assertThat(stored.getTokenHash()).isNotEqualTo(issued.rawToken());
		assertThat(ENCODER.matches(issued.rawToken(), stored.getTokenHash())).isTrue();
	}

	@Test
	void claimingActivatesTheUserAndSetsItsRealPassword() {
		Organisation organisation = newOrg("claim");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("claimant@staff-invite.test", null);
		TenantContext.clear();

		LoginResult result = staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "MyOwnPass123!");

		assertThat(result.organisationId()).isEqualTo(organisation.getId());
		User user = userRepository.findByEmail("claimant@staff-invite.test").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(ENCODER.matches("MyOwnPass123!", user.getPasswordHash())).isTrue();

		TenantContext.set(organisation.getId(), null);
		assertThat(staffInviteRepository.findById(issued.inviteId()).orElseThrow().getStatus()).isEqualTo(StaffInviteStatus.CLAIMED);
	}

	@Test
	void claimingRejectsAWrongToken() {
		Organisation organisation = newOrg("wrong-token");
		TenantContext.set(organisation.getId(), null);
		staffInviteService().issue("wrong-token@staff-invite.test", null);
		TenantContext.clear();

		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), "not-the-real-token", "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void claimingRejectsAnExpiredInvite() {
		Organisation organisation = newOrg("expired");
		TenantContext.set(organisation.getId(), null);
		Long userId = userRepository.save(new User("expired@staff-invite.test", null, ENCODER.encode("placeholder"))).getId();
		String rawToken = "expired-raw-token";
		staffInviteRepository.save(new StaffInvite(userId, ENCODER.encode(rawToken), Instant.now().minusSeconds(60)));
		TenantContext.clear();

		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), rawToken, "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void claimingTwiceWithTheSameTokenFailsTheSecondTime() {
		Organisation organisation = newOrg("double-claim");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("double-claim@staff-invite.test", null);
		TenantContext.clear();

		staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "FirstClaimPass1!");

		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "SecondClaimPass1!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void anInviteFromOneOrgCannotBeClaimedThroughAnotherOrgsSlug() {
		Organisation orgA = newOrg("tenant-a");
		TenantContext.set(orgA.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("cross-tenant@staff-invite.test", null);
		TenantContext.clear();

		Organisation orgB = newOrg("tenant-b");
		TenantContext.clear();

		assertThatThrownBy(() -> staffInviteService().claim(orgB.getSlug(), issued.rawToken(), "SomePass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}
}
