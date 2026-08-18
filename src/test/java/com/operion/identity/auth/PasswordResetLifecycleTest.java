package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
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

/** Covers #42 - forgot password. Same shape as PortalInviteLifecycleTest, plus the
 * anti-enumeration property that's unique to this flow: requesting a reset for an email or
 * org that doesn't exist must be a silent no-op, never a distinguishable error or a token. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetLifecycleTest {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	private PasswordResetService passwordResetService() {
		return new PasswordResetService(passwordResetTokenRepository, userRepository, organisationRepository, ENCODER);
	}

	private Organisation newOrg(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void requestingAResetNeverStoresTheRawToken() {
		Organisation organisation = newOrg("request");
		TenantContext.set(organisation.getId(), null);
		userRepository.save(new User("reset-me@pw-reset.test", null, ENCODER.encode("OldPass123!")));
		TenantContext.clear();

		passwordResetService().requestReset(organisation.getSlug(), "reset-me@pw-reset.test");

		TenantContext.set(organisation.getId(), null);
		var tokens = passwordResetTokenRepository.findByConsumedFalse();
		assertThat(tokens).hasSize(1);
		assertThat(tokens.get(0).getTokenHash()).doesNotContain("reset-me");
	}

	@Test
	void requestingAResetForAnUnknownEmailIsASilentNoOpAndCreatesNoToken() {
		Organisation organisation = newOrg("unknown-email");

		passwordResetService().requestReset(organisation.getSlug(), "nobody@pw-reset.test");

		TenantContext.set(organisation.getId(), null);
		assertThat(passwordResetTokenRepository.findByConsumedFalse()).isEmpty();
	}

	@Test
	void requestingAResetForAnUnknownOrgIsASilentNoOp() {
		passwordResetService().requestReset("no-such-org-" + System.nanoTime(), "someone@pw-reset.test");
		// No exception, nothing to assert on a token table we have no tenant context to query.
	}

	@Test
	void confirmingUpdatesThePasswordAndConsumesTheToken() {
		Organisation organisation = newOrg("confirm");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("confirm-me@pw-reset.test", null, ENCODER.encode("OldPass123!")));
		String rawToken = "raw-reset-token";
		PasswordResetToken token = passwordResetTokenRepository
				.save(new PasswordResetToken(user.getId(), ENCODER.encode(rawToken), Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		passwordResetService().confirmReset(organisation.getSlug(), rawToken, "NewPass123!");

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(ENCODER.matches("NewPass123!", reloaded.getPasswordHash())).isTrue();
		TenantContext.set(organisation.getId(), null);
		assertThat(passwordResetTokenRepository.findById(token.getId()).orElseThrow().isConsumed()).isTrue();
	}

	@Test
	void confirmingRejectsAWrongToken() {
		Organisation organisation = newOrg("wrong-token");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("wrong-token@pw-reset.test", null, ENCODER.encode("OldPass123!")));
		passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), ENCODER.encode("real-token"), Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		assertThatThrownBy(() -> passwordResetService().confirmReset(organisation.getSlug(), "not-the-real-token", "NewPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void confirmingRejectsAnExpiredToken() {
		Organisation organisation = newOrg("expired");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("expired@pw-reset.test", null, ENCODER.encode("OldPass123!")));
		String rawToken = "expired-raw-token";
		passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), ENCODER.encode(rawToken), Instant.now().minusSeconds(60)));
		TenantContext.clear();

		assertThatThrownBy(() -> passwordResetService().confirmReset(organisation.getSlug(), rawToken, "NewPass123!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void confirmingTwiceWithTheSameTokenFailsTheSecondTime() {
		Organisation organisation = newOrg("double-confirm");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("double-confirm@pw-reset.test", null, ENCODER.encode("OldPass123!")));
		String rawToken = "double-confirm-token";
		passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), ENCODER.encode(rawToken), Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		passwordResetService().confirmReset(organisation.getSlug(), rawToken, "FirstNewPass1!");

		assertThatThrownBy(() -> passwordResetService().confirmReset(organisation.getSlug(), rawToken, "SecondNewPass1!"))
				.isInstanceOf(AuthenticationFailedException.class);
	}
}
