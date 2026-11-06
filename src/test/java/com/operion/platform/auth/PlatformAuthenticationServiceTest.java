package com.operion.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.identity.auth.LoginAttemptRepository;
import com.operion.identity.auth.LoginAttemptService;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import com.operion.platform.PlatformAdminStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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

	@Autowired
	private LoginAttemptRepository loginAttemptRepository;

	private PlatformAuthenticationService authenticationService() {
		return authenticationService(5);
	}

	private PlatformAuthenticationService authenticationService(int maxFailedAttempts) {
		return new PlatformAuthenticationService(platformAdminRepository, ENCODER, new PlatformJwtService(TEST_SECRET, 60),
				new LoginAttemptService(loginAttemptRepository, maxFailedAttempts, 15));
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

	@Test
	void locksTheAccountAfterRepeatedFailuresEvenForTheCorrectPassword() {
		platformAdminRepository.save(new PlatformAdmin("Erin", "erin@operion.platform", ENCODER.encode("correct-horse")));
		PlatformAuthenticationService service = authenticationService(3);

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> service.login("erin@operion.platform", "wrong-password"))
					.isInstanceOf(PlatformAuthenticationFailedException.class);
		}
		assertThatThrownBy(() -> service.login("erin@operion.platform", "correct-horse"))
				.isInstanceOf(PlatformAuthenticationFailedException.class)
				.hasMessage("Invalid email or password");
		assertThat(new LoginAttemptService(loginAttemptRepository, 3, 15).isLocked(1L, "erin@operion.platform"))
				.as("a school account with the same email is not affected").isFalse();
	}

	@Test
	void aSuccessfulLoginClearsEarlierFailures() {
		platformAdminRepository.save(new PlatformAdmin("Fay", "fay@operion.platform", ENCODER.encode("correct-horse")));
		PlatformAuthenticationService service = authenticationService(3);

		assertThatThrownBy(() -> service.login("fay@operion.platform", "wrong-password"));
		assertThatThrownBy(() -> service.login("fay@operion.platform", "wrong-password"));
		service.login("fay@operion.platform", "correct-horse");
		assertThatThrownBy(() -> service.login("fay@operion.platform", "wrong-password"));
		assertThatThrownBy(() -> service.login("fay@operion.platform", "wrong-password"));

		assertThat(service.login("fay@operion.platform", "correct-horse").token()).isNotBlank();
	}

	@Test
	void aDisabledAdminsExistingTokenStopsWorkingImmediately() throws Exception {
		PlatformAdmin admin = platformAdminRepository.save(
				new PlatformAdmin("Gus", "gus@operion.platform", ENCODER.encode("correct-horse")));
		PlatformJwtService jwt = new PlatformJwtService(TEST_SECRET, 60);
		String token = authenticationService().login("gus@operion.platform", "correct-horse").token();
		PlatformAuthenticationInterceptor interceptor = new PlatformAuthenticationInterceptor(jwt, platformAdminRepository);

		assertThat(interceptor.preHandle(platformRequest(token), new MockHttpServletResponse(), null)).isTrue();

		admin.changeStatus(PlatformAdminStatus.INACTIVE);
		platformAdminRepository.save(admin);
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(platformRequest(token), response, null)).isFalse();
		assertThat(response.getStatus()).isEqualTo(401);
	}

	private static MockHttpServletRequest platformRequest(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/organisations");
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}
}
