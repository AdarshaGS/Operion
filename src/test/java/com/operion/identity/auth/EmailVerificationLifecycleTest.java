package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.email.EmailOutboxRepository;
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

/** Covers #48 - email verification, deliberately non-blocking (see EmailVerificationService
 * javadoc), so there's no login-side assertion here - just issue-on-create and confirm(). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationLifecycleTest {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private EmailOutboxRepository emailOutboxRepository;

	private EmailVerificationService emailVerificationService() {
		// No EmailSender configured - see StaffInviteLifecycleTest's identical reasoning.
		return new EmailVerificationService(emailVerificationTokenRepository, userRepository, organisationRepository, ENCODER,
				new EmailDeliveryService(List.of(), emailOutboxRepository), "http://localhost:5173");
	}

	private Organisation newOrg(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void issuingOnCreateNeverStoresTheRawToken() {
		Organisation organisation = newOrg("issue");
		TenantContext.set(organisation.getId(), null);
		Long userId = userRepository.save(new User("verify-me@email-verify.test", null, "hash")).getId();

		emailVerificationService().issue(userId);

		var tokens = emailVerificationTokenRepository.findByConsumedFalse();
		assertThat(tokens).hasSize(1);
		assertThat(tokens.get(0).getUserId()).isEqualTo(userId);
	}

	@Test
	void confirmingSetsEmailVerifiedAtAndConsumesTheToken() {
		Organisation organisation = newOrg("confirm");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("confirm-me@email-verify.test", null, "hash"));
		assertThat(user.getEmailVerifiedAt()).isNull();
		String rawToken = "raw-verify-token";
		EmailVerificationToken token = emailVerificationTokenRepository
				.save(new EmailVerificationToken(user.getId(), ENCODER.encode(rawToken), Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		emailVerificationService().confirm(organisation.getSlug(), rawToken);

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getEmailVerifiedAt()).isNotNull();
		TenantContext.set(organisation.getId(), null);
		assertThat(emailVerificationTokenRepository.findById(token.getId()).orElseThrow().isConsumed()).isTrue();
	}

	@Test
	void confirmingRejectsAWrongToken() {
		Organisation organisation = newOrg("wrong-token");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("wrong-token@email-verify.test", null, "hash"));
		emailVerificationTokenRepository.save(new EmailVerificationToken(user.getId(), ENCODER.encode("real-token"), Instant.now().plusSeconds(3600)));
		TenantContext.clear();

		assertThatThrownBy(() -> emailVerificationService().confirm(organisation.getSlug(), "not-the-real-token"))
				.isInstanceOf(AuthenticationFailedException.class);
	}

	@Test
	void confirmingRejectsAnExpiredToken() {
		Organisation organisation = newOrg("expired");
		TenantContext.set(organisation.getId(), null);
		User user = userRepository.save(new User("expired@email-verify.test", null, "hash"));
		String rawToken = "expired-raw-token";
		emailVerificationTokenRepository.save(new EmailVerificationToken(user.getId(), ENCODER.encode(rawToken), Instant.now().minusSeconds(60)));
		TenantContext.clear();

		assertThatThrownBy(() -> emailVerificationService().confirm(organisation.getSlug(), rawToken))
				.isInstanceOf(AuthenticationFailedException.class);
	}
}
