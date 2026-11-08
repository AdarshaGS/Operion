package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.email.EmailOutboxRepository;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserService;
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
import tools.jackson.databind.ObjectMapper;

/** A staff invite is only good for the PENDING account it was issued for: a stale link must not re-enable a disabled
 * user or overwrite an active user's password, and disabling a user kills any invite still waiting to be claimed. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffInviteStaleClaimTest {

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
	@Autowired
	private EmailOutboxRepository emailOutboxRepository;
	@Autowired
	private OrganisationMembershipRepository membershipRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;

	private StaffInviteService staffInviteService() {
		return new StaffInviteService(staffInviteRepository, userRepository, organisationRepository, ENCODER,
				new JwtService(TEST_SECRET, 480), new RefreshTokenService(refreshTokenRepository, ENCODER),
				new EmailDeliveryService(List.of(), emailOutboxRepository), "http://localhost:5173");
	}

	private UserService userService() {
		return new UserService(userRepository, new AuditLogService(auditLogRepository, new ObjectMapper()), membershipRepository,
				staffInviteRepository);
	}

	private Organisation newOrg(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void aStaleInviteCannotReEnableADisabledUser() {
		Organisation organisation = newOrg("stale-disabled");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("leaver@stale-invite.test", null);
		Long userId = userRepository.findByEmail("leaver@stale-invite.test").orElseThrow().getId();
		// Disabled directly on the row (not via UserService), so the invite is still PENDING - the stale-link case.
		User user = userRepository.findById(userId).orElseThrow();
		user.setStatus(UserStatus.DISABLED);
		userRepository.save(user);
		TenantContext.clear();

		// Claim must neither re-enable the account nor hand out a session, and it burns the stale invite.
		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "MyOwnPass123!"))
				.isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid or expired invite");

		TenantContext.set(organisation.getId(), null);
		User reloaded = userRepository.findById(userId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);
		assertThat(ENCODER.matches("MyOwnPass123!", reloaded.getPasswordHash())).isFalse();
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).isEmpty();
		assertThat(staffInviteRepository.findById(issued.inviteId()).orElseThrow().getStatus()).isEqualTo(StaffInviteStatus.REVOKED);
	}

	@Test
	void aStaleInviteCannotReEnableALockedUser() {
		Organisation organisation = newOrg("stale-locked");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("locked@stale-invite.test", null);
		User user = userRepository.findByEmail("locked@stale-invite.test").orElseThrow();
		user.setStatus(UserStatus.LOCKED);
		userRepository.save(user);
		TenantContext.clear();

		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "MyOwnPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);

		TenantContext.set(organisation.getId(), null);
		assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.LOCKED);
	}

	@Test
	void aStaleInviteCannotOverwriteAnActiveUsersPassword() {
		Organisation organisation = newOrg("stale-active");
		TenantContext.set(organisation.getId(), null);
		User existing = userRepository.save(new User("active@stale-invite.test", null, ENCODER.encode("TheirRealPass1!")));
		String rawToken = "a-leftover-raw-token";
		staffInviteRepository.save(new StaffInvite(existing.getId(), ENCODER.encode(rawToken),
				java.time.Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), rawToken, "AttackerChosen1!"))
				.isInstanceOf(AuthenticationFailedException.class);

		TenantContext.set(organisation.getId(), null);
		User reloaded = userRepository.findById(existing.getId()).orElseThrow();
		assertThat(ENCODER.matches("TheirRealPass1!", reloaded.getPasswordHash())).isTrue();
		assertThat(ENCODER.matches("AttackerChosen1!", reloaded.getPasswordHash())).isFalse();
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(existing.getId())).isEmpty();
	}

	@Test
	void disablingAUserRevokesItsPendingInvites() {
		Organisation organisation = newOrg("disable-revokes");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("revoked@stale-invite.test", null);
		Long userId = userRepository.findByEmail("revoked@stale-invite.test").orElseThrow().getId();

		userService().changeStatus(userId, UserStatus.DISABLED);

		assertThat(staffInviteRepository.findById(issued.inviteId()).orElseThrow().getStatus()).isEqualTo(StaffInviteStatus.REVOKED);
		TenantContext.clear();
		assertThatThrownBy(() -> staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "MyOwnPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
		TenantContext.set(organisation.getId(), null);
		assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);
	}

	@Test
	void aNormalPendingInviteStillClaimsAndActivates() {
		Organisation organisation = newOrg("normal");
		TenantContext.set(organisation.getId(), null);
		StaffInviteService.IssuedInvite issued = staffInviteService().issue("normal@stale-invite.test", null);
		TenantContext.clear();

		LoginResult result = staffInviteService().claim(organisation.getSlug(), issued.rawToken(), "MyOwnPass123!");

		assertThat(result.token()).isNotBlank();
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.findByEmail("normal@stale-invite.test").orElseThrow();
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(ENCODER.matches("MyOwnPass123!", user.getPasswordHash())).isTrue();
	}
}
