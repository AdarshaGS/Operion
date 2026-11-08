package com.operion.identity.auth.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.Role;
import com.operion.common.SlidingWindowRateLimiter;
import com.operion.common.TenantContext;
import com.operion.common.TooManyRequestsException;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.AuthRateLimiter;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.identity.auth.AuthenticationService;
import com.operion.identity.auth.EmailVerificationService;
import com.operion.identity.auth.PasswordResetService;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.identity.auth.StaffInviteService;
import com.operion.identity.auth.WrongCurrentPasswordException;
import com.operion.modules.ModuleAccessService;
import com.operion.organisation.Campus;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthenticationService authenticationService;
	private final OrganisationMembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final StaffInviteService staffInviteService;
	private final RefreshTokenService refreshTokenService;
	private final PasswordResetService passwordResetService;
	private final EmailVerificationService emailVerificationService;
	private final ModuleAccessService moduleAccessService;
	private final AuthRateLimiter authRateLimiter;
	/**
	 * Per caller address, one bucket per public token endpoint so one endpoint's attempts never use up another's (SEC-M3).
	 * Same limiter and address derivation (AuthRateLimiter.clientIp -> ClientIpResolver) as claim-invite in
	 * PortalInviteAuthController. Only FAILED attempts count (like failed logins): the tokens are high-entropy secrets,
	 * so guessing is what needs capping, and a school onboarding many staff from one NAT address must not be locked
	 * out by its own successful claims. Refresh is far more generous because legitimate clients call it whenever an
	 * access token expires.
	 */
	private final SlidingWindowRateLimiter claimStaffInviteLimiter = new SlidingWindowRateLimiter(20, Duration.ofMinutes(10), 10_000);
	private final SlidingWindowRateLimiter passwordResetConfirmLimiter = new SlidingWindowRateLimiter(20, Duration.ofMinutes(10), 10_000);
	private final SlidingWindowRateLimiter refreshLimiter = new SlidingWindowRateLimiter(30, Duration.ofMinutes(1), 10_000);

	public AuthController(AuthenticationService authenticationService, OrganisationMembershipRepository membershipRepository,
			UserRepository userRepository, OrganisationRepository organisationRepository,
			StaffInviteService staffInviteService, RefreshTokenService refreshTokenService, PasswordResetService passwordResetService,
			EmailVerificationService emailVerificationService, ModuleAccessService moduleAccessService, AuthRateLimiter authRateLimiter) {
		this.authenticationService = authenticationService;
		this.membershipRepository = membershipRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.staffInviteService = staffInviteService;
		this.refreshTokenService = refreshTokenService;
		this.passwordResetService = passwordResetService;
		this.emailVerificationService = emailVerificationService;
		this.moduleAccessService = moduleAccessService;
		this.authRateLimiter = authRateLimiter;
	}

	/** Besides the per-account lockout inside login(), a per-IP cap on FAILED logins (AuthRateLimiter) stops one
	 * source spraying many accounts; over it, 429 whatever the credentials. A missing password is not a 400 - it
	 * is rejected exactly like a wrong one, see LoginRequest. */
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String ip = authRateLimiter.clientIp(httpRequest);
		authRateLimiter.checkLoginAllowed(ip);
		try {
			return LoginResponse.from(authenticationService.login(request.organisationSlug(), request.email(), request.password()));
		} catch (AuthenticationFailedException ex) {
			authRateLimiter.recordLoginFailure(ip);
			throw ex;
		}
	}

	/** Public, unauthenticated - the whole point is exchanging a refresh token for a new
	 * access token once the old access token has already expired, see AuthenticationService.refresh(). */
	@PostMapping("/refresh")
	public LoginResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
		return throttled(refreshLimiter, httpRequest,
				() -> LoginResponse.from(authenticationService.refresh(request.organisationSlug(), request.refreshToken())));
	}

	/** Public, unauthenticated - same trust tier as claim-invite, see StaffInviteService.claim(). */
	@PostMapping("/claim-staff-invite")
	public LoginResponse claimStaffInvite(@Valid @RequestBody ClaimStaffInviteRequest request, HttpServletRequest httpRequest) {
		return throttled(claimStaffInviteLimiter, httpRequest,
				() -> LoginResponse.from(staffInviteService.claim(request.organisationSlug(), request.token(), request.password())));
	}

	/** 429 once this address has {@code limiter}'s allowance of failed attempts in the window; successes are not counted. */
	private <T> T throttled(SlidingWindowRateLimiter limiter, HttpServletRequest httpRequest, java.util.function.Supplier<T> call) {
		String ip = authRateLimiter.clientIp(httpRequest);
		if (limiter.isBlocked(ip)) {
			throw new TooManyRequestsException("Too many attempts. Please try again later.");
		}
		try {
			return call.get();
		} catch (RuntimeException failure) {
			limiter.tryAcquire(ip);
			throw failure;
		}
	}

	/** "Sign out everywhere" - see RefreshTokenService.revokeAllForUser(). Authenticated
	 * (bearer-protected like every other non-bootstrap endpoint): the caller's own token is
	 * what resolves which user's sessions to revoke. */
	@PostMapping("/logout")
	public AckResponse logout() {
		refreshTokenService.revokeAllForUser(TenantContext.getActorId());
		return new AckResponse("Logged out");
	}

	/** Authenticated - see AuthenticationService.changePassword() for why the "current
	 * password wrong" message can be specific here unlike everywhere else in this class. */
	@PutMapping("/password")
	public AckResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
		// Guess-limited per user (inside changePassword) and per IP (here, wrong-password attempts only).
		String ip = authRateLimiter.clientIp(httpRequest);
		authRateLimiter.checkPasswordChangeAllowed(ip);
		try {
			authenticationService.changePassword(TenantContext.getActorId(), request.currentPassword(), request.newPassword());
		} catch (WrongCurrentPasswordException ex) {
			authRateLimiter.recordPasswordChangeFailure(ip);
			throw ex;
		}
		return new AckResponse("Password updated");
	}

	/** Public, unauthenticated - see PasswordResetService for why the response never varies
	 * with whether the org/email actually matched anything. Throttled per email and per IP (AuthRateLimiter),
	 * and a throttled request gets the very same acknowledgement - it just sends nothing - so the throttle
	 * cannot be used to probe for accounts either. */
	@PostMapping("/password-reset/request")
	public AckResponse requestPasswordReset(@Valid @RequestBody RequestPasswordResetRequest request, HttpServletRequest httpRequest) {
		if (authRateLimiter.allowResetRequest(authRateLimiter.clientIp(httpRequest), request.organisationSlug(), request.email())) {
			passwordResetService.requestReset(request.organisationSlug(), request.email());
		}
		return new AckResponse("If that account exists, a reset link has been sent");
	}

	/** Public, unauthenticated. */
	@PostMapping("/password-reset/confirm")
	public AckResponse confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request, HttpServletRequest httpRequest) {
		return throttled(passwordResetConfirmLimiter, httpRequest, () -> {
			passwordResetService.confirmReset(request.organisationSlug(), request.token(), request.newPassword());
			return new AckResponse("Password reset");
		});
	}

	/** Public, unauthenticated - see EmailVerificationService. */
	@PostMapping("/verify-email")
	public AckResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		emailVerificationService.confirm(request.organisationSlug(), request.token());
		return new AckResponse("Email verified");
	}

	/**
	 * Echoes what the interceptor resolved from the caller's token, the same permission
	 * set PermissionInterceptor itself checks requests against (lets the frontend
	 * hide/disable actions instead of only finding out via a 403 on submit), and the
	 * caller's own display identity (person name / active role(s) / org name) so the UI
	 * has something to show for "who am I logged in as" beyond a bare user id.
	 */
	@GetMapping("/me")
	public MeResponse me() {
		Long userId = TenantContext.getActorId();
		Long organisationId = TenantContext.getOrganisationId();

		// findByUserId is tenant-scoped (@TenantId on OrganisationMembership), so this
		// only ever sees memberships in the org the caller's token is scoped to.
		List<OrganisationMembership> activeMemberships = membershipRepository.findByUserId(userId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.toList();

		// One user maps to one Person within a given org (multiple roles are multiple
		// membership rows sharing that same person, per the identity model) - take the
		// first membership's person as the display identity.
		Long personId = activeMemberships.stream().findFirst().map(membership -> membership.getPerson().getId()).orElse(null);
		String personName = activeMemberships.stream()
				.findFirst()
				.map(membership -> membership.getPerson().getFirstName() + " " + membership.getPerson().getLastName())
				.orElse(null);
		List<String> roleNames = activeMemberships.stream().map(membership -> membership.getRole().getName()).distinct().toList();
		List<RoleSummary> roles = activeMemberships.stream()
				.map(OrganisationMembership::getRole)
				.collect(Collectors.toMap(Role::getId, role -> new RoleSummary(role.getName(), role.getDescription()), (a, b) -> a,
						LinkedHashMap::new))
				.values()
				.stream()
				.toList();

		String firstName = activeMemberships.stream().findFirst().map(membership -> membership.getPerson().getFirstName()).orElse(null);
		String lastName = activeMemberships.stream().findFirst().map(membership -> membership.getPerson().getLastName()).orElse(null);
		String campusName = activeMemberships.stream()
				.findFirst()
				.map(OrganisationMembership::getCampus)
				.map(Campus::getName)
				.orElse(null);
		String status = activeMemberships.stream().findFirst().map(membership -> membership.getStatus().name()).orElse(null);

		String email = userRepository.findById(userId).map(User::getEmail).orElse(null);
		String organisationName = organisationRepository.findById(organisationId).map(Organisation::getName).orElse(null);

		return new MeResponse(userId, organisationId, organisationName, email, personId, personName, firstName, lastName,
				campusName, status, roleNames, roles, membershipRepository.findActivePermissionCodesForUser(userId),
				moduleAccessService.disabledModules(organisationId).stream().map(Enum::name).collect(Collectors.toSet()),
				membershipRepository.existsByUserIdAndStatusAndOwner(userId, MembershipStatus.ACTIVE, true));
	}
}
