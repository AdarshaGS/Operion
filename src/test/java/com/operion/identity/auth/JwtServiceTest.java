package com.operion.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private final JwtService jwtService = new JwtService("unit-test-secret-key-not-for-real-use-32-bytes-min", 480);

	@Test
	void issuedTokenDecodesBackToTheSameUserAndOrganisation() {
		JwtService.IssuedToken issued = jwtService.issue(42L, 7L);

		JwtService.TokenPrincipal principal = jwtService.decodeToPrincipal(issued.token());

		assertThat(principal.userId()).isEqualTo(42L);
		assertThat(principal.organisationId()).isEqualTo(7L);
	}

	@Test
	void rejectsATokenSignedWithADifferentSecret() {
		JwtService otherService = new JwtService("a-completely-different-secret-key-also-32-bytes-min", 480);
		String foreignToken = otherService.issue(1L, 1L).token();

		assertThatThrownBy(() -> jwtService.decodeToPrincipal(foreignToken))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void rejectsGarbageInput() {
		assertThatThrownBy(() -> jwtService.decodeToPrincipal("not-a-real-token"))
				.isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void rejectsAnAlreadyExpiredToken() {
		JwtService expiringImmediately = new JwtService("unit-test-secret-key-not-for-real-use-32-bytes-min", 0);
		String token = expiringImmediately.issue(1L, 1L).token();

		assertThatThrownBy(() -> expiringImmediately.decodeToPrincipal(token))
				.isInstanceOf(InvalidTokenException.class);
	}
}
