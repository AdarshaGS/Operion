package com.operion.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import com.operion.platform.PlatformAdminStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlatformAuthenticationService/PlatformJwtService are constructed by hand rather than
 * @Import'd - unlike most services in this codebase they don't depend on AuditLogService,
 * but @DataJpaTest's slice has no PasswordEncoder bean (SecurityConfig isn't part of the
 * slice) and no @Value-resolved JWT secret either, so hand construction sidesteps both
 * rather than pulling in more config than this test needs.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformAuthenticationServiceTest {

	private static final String TEST_SECRET = "test-only-platform-secret-at-least-32-bytes-long";
	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired
	private PlatformAdminRepository platformAdminRepository;

	private PlatformAuthenticationService authenticationService() {
		return new PlatformAuthenticationService(platformAdminRepository, ENCODER, new PlatformJwtService(TEST_SECRET, 60));
	}

	@Test
	void issuesATokenThatDecodesBackToTheAdminForCorrectCredentials() {
		PlatformAdmin admin = platformAdminRepository.save(
				new PlatformAdmin("Alice", "alice@operion.platform", ENCODER.encode("correct-horse")));

		PlatformAuthenticationService.PlatformLoginResult result = authenticationService().login("alice@operion.platform", "correct-horse");

		assertThat(result.token()).isNotBlank();
		assertThat(result.platformAdminId()).isEqualTo(admin.getId());
		assertThat(new PlatformJwtService(TEST_SECRET, 60).decodeToPlatformAdminId(result.token())).isEqualTo(admin.getId());
	}

	@Test
	void rejectsWrongPassword() {
		platformAdminRepository.save(new PlatformAdmin("Bob", "bob@operion.platform", ENCODER.encode("correct-horse")));

		assertThatThrownBy(() -> authenticationService().login("bob@operion.platform", "wrong-password"))
				.isInstanceOf(PlatformAuthenticationFailedException.class);
	}

	@Test
	void rejectsUnknownEmail() {
		assertThatThrownBy(() -> authenticationService().login("nobody@operion.platform", "whatever"))
				.isInstanceOf(PlatformAuthenticationFailedException.class);
	}

	@Test
	void rejectsAnInactiveAdminEvenWithTheCorrectPassword() {
		PlatformAdmin admin = platformAdminRepository.save(
				new PlatformAdmin("Carol", "carol@operion.platform", ENCODER.encode("correct-horse")));
		admin.changeStatus(PlatformAdminStatus.INACTIVE);
		platformAdminRepository.save(admin);

		assertThatThrownBy(() -> authenticationService().login("carol@operion.platform", "correct-horse"))
				.isInstanceOf(PlatformAuthenticationFailedException.class);
	}

	@Test
	void rejectsATokenSignedWithADifferentSecret() {
		platformAdminRepository.save(new PlatformAdmin("Dave", "dave@operion.platform", ENCODER.encode("correct-horse")));
		String token = authenticationService().login("dave@operion.platform", "correct-horse").token();

		PlatformJwtService differentSecretService = new PlatformJwtService("a-completely-different-secret-also-32-bytes-min", 60);

		assertThatThrownBy(() -> differentSecretService.decodeToPlatformAdminId(token))
				.isInstanceOf(InvalidPlatformTokenException.class);
	}
}
