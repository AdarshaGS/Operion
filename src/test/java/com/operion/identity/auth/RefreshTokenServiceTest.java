package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.authorization.OrganisationMembershipRepository;
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

/** Covers #41 - logout revokes every active refresh token for the calling user (not just
 * one, since the frontend never stores the token it's issued - see
 * RefreshTokenService.revokeAllForUser()'s own javadoc), and a revoked token really can't
 * mint a new access token afterwards. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenServiceTest {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private OrganisationMembershipRepository membershipRepository;

	private RefreshTokenService refreshTokenService() {
		return new RefreshTokenService(refreshTokenRepository, ENCODER);
	}

	private Organisation newOrg(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void revokeAllForUserRevokesEveryActiveTokenForThatUserButNotOthers() {
		Organisation organisation = newOrg("logout");
		TenantContext.set(organisation.getId(), null);
		Long userId = userRepository.save(new User("logout-me@refresh-svc.test", null, "hash")).getId();
		Long otherUserId = userRepository.save(new User("other@refresh-svc.test", null, "hash")).getId();
		RefreshTokenService service = refreshTokenService();
		service.issue(userId);
		service.issue(userId);
		service.issue(otherUserId);

		service.revokeAllForUser(userId);

		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(userId)).isEmpty();
		assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(otherUserId)).hasSize(1);
	}

	@Test
	void aRevokedTokenCannotBeUsedToMintANewAccessToken() {
		Organisation organisation = newOrg("revoked-refresh");
		TenantContext.set(organisation.getId(), null);
		Long userId = userRepository.save(new User("revoked@refresh-svc.test", null, "hash")).getId();
		RefreshTokenService service = refreshTokenService();
		RefreshTokenService.IssuedRefreshToken issued = service.issue(userId);
		service.revokeAllForUser(userId);
		TenantContext.clear();

		AuthenticationService authenticationService = new AuthenticationService(organisationRepository, userRepository,
				membershipRepository, ENCODER, new JwtService("test-only-secret-not-for-real-use-but-still-32-bytes-min", 480),
				service, new LoginAttemptService(5, 15));

		assertThatThrownBy(() -> authenticationService.refresh(organisation.getSlug(), issued.rawToken()))
				.isInstanceOf(AuthenticationFailedException.class);
	}
}
