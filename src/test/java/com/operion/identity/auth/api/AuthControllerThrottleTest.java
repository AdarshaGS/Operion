package com.operion.identity.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TooManyRequestsException;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.AuthRateLimiter;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.identity.auth.AuthenticationService;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.PasswordResetService;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.identity.auth.StaffInviteService;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.OrganisationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEC-M3: the public token endpoints (refresh, claim-staff-invite, password-reset confirm) answer 429 once one
 * address has used up its allowance, each with its own bucket, and one address does not throttle another.
 */
class AuthControllerThrottleTest {

	private AuthRateLimiter authRateLimiter;
	private AuthenticationService authenticationService;
	private StaffInviteService staffInviteService;
	private PasswordResetService passwordResetService;
	private AuthController controller;
	private HttpServletRequest fromA;
	private HttpServletRequest fromB;

	@BeforeEach
	void setUp() {
		authRateLimiter = mock(AuthRateLimiter.class);
		authenticationService = mock(AuthenticationService.class);
		staffInviteService = mock(StaffInviteService.class);
		passwordResetService = mock(PasswordResetService.class);
		fromA = mock(HttpServletRequest.class);
		fromB = mock(HttpServletRequest.class);
		when(authRateLimiter.clientIp(fromA)).thenReturn("10.0.0.1");
		when(authRateLimiter.clientIp(fromB)).thenReturn("10.0.0.2");
		// Bad tokens: only the throttling in front of the services is under test.
		when(authenticationService.refresh(any(), any())).thenThrow(new AuthenticationFailedException("bad"));
		when(staffInviteService.claim(any(), any(), any())).thenThrow(new AuthenticationFailedException("bad"));
		org.mockito.Mockito.doThrow(new AuthenticationFailedException("bad")).when(passwordResetService).confirmReset(any(), any(), any());
		controller = new AuthController(authenticationService, mock(OrganisationMembershipRepository.class), mock(UserRepository.class),
				mock(OrganisationRepository.class), staffInviteService, mock(RefreshTokenService.class), passwordResetService,
				mock(EmailVerificationService.class), mock(ModuleAccessService.class), authRateLimiter);
	}

	private void refresh(HttpServletRequest request) {
		controller.refresh(new RefreshRequest("acme", "bad-token"), request);
	}

	private void claimStaffInvite(HttpServletRequest request) {
		controller.claimStaffInvite(new ClaimStaffInviteRequest("acme", "bad-token", "Password!12345"), request);
	}

	private void confirmReset(HttpServletRequest request) {
		controller.confirmPasswordReset(new ConfirmPasswordResetRequest("acme", "bad-token", "Password!12345"), request);
	}

	/** Runs the call until it is throttled and returns how many attempts went through before that. */
	private int attemptsBeforeThrottle(Consumer<HttpServletRequest> call, HttpServletRequest request) {
		for (int i = 0; i < 100; i++) {
			try {
				call.accept(request);
			} catch (TooManyRequestsException ex) {
				return i;
			} catch (AuthenticationFailedException ex) {
				// a bad token: expected until throttled
			}
		}
		return -1;
	}

	@Test
	void claimStaffInviteAndResetConfirmAllowTwentyAttemptsThenRefuse() {
		assertThat(attemptsBeforeThrottle(this::claimStaffInvite, fromA)).isEqualTo(20);
		assertThat(attemptsBeforeThrottle(this::confirmReset, fromA)).isEqualTo(20);
	}

	@Test
	void refreshAllowsThirtyAttemptsThenRefusesWellInsideThirtyFive() {
		assertThat(attemptsBeforeThrottle(this::refresh, fromA)).isEqualTo(30);
	}

	@Test
	void endpointsHaveSeparateBuckets() {
		assertThat(attemptsBeforeThrottle(this::claimStaffInvite, fromA)).isEqualTo(20);
		// claim-staff-invite is exhausted for this address; the others are untouched.
		assertThatThrownBy(() -> claimStaffInvite(fromA)).isInstanceOf(TooManyRequestsException.class);
		assertThat(attemptsBeforeThrottle(this::confirmReset, fromA)).isEqualTo(20);
		assertThat(attemptsBeforeThrottle(this::refresh, fromA)).isEqualTo(30);
	}

	@Test
	void oneAddressBeingThrottledDoesNotBlockAnother() {
		assertThat(attemptsBeforeThrottle(this::confirmReset, fromA)).isEqualTo(20);
		// B still reaches the service (a bad token, not a 429).
		assertThatThrownBy(() -> confirmReset(fromB)).isInstanceOf(AuthenticationFailedException.class);
		verify(passwordResetService, times(21)).confirmReset(any(), any(), any());
	}

	@Test
	void successfulClaimsNeverCountTowardsTheLimit() {
		// A school onboarding many staff from one office address must not lock itself out (only failed guesses count).
		org.mockito.Mockito.reset(staffInviteService);
		when(staffInviteService.claim(any(), any(), any())).thenReturn(new com.operion.identity.auth.LoginResult("t", null, "r", null, 1L, 1L));
		for (int i = 0; i < 100; i++) {
			claimStaffInvite(fromA);
		}
		verify(staffInviteService, times(100)).claim(any(), any(), any());
	}
}
