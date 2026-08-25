package com.operion.identity.auth;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

	private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

	private final OrganisationRepository organisationRepository;
	private final UserRepository userRepository;
	private final OrganisationMembershipRepository membershipRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final LoginAttemptService loginAttemptService;

	public AuthenticationService(OrganisationRepository organisationRepository, UserRepository userRepository,
			OrganisationMembershipRepository membershipRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService, LoginAttemptService loginAttemptService) {
		this.organisationRepository = organisationRepository;
		this.userRepository = userRepository;
		this.membershipRepository = membershipRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.loginAttemptService = loginAttemptService;
	}

	public LoginResult login(String organisationSlug, String email, String rawPassword) {
		// Deliberately the same error for "no such org", "no such user", "wrong password",
		// "inactive status" and "no membership" - distinguishing them lets an attacker
		// enumerate valid slugs/emails/account states. Same reasoning covers the lockout
		// check below: it fails closed with this exact message too, rather than a distinct
		// "too many attempts" response that would itself leak "this email exists".
		AuthenticationFailedException failure = new AuthenticationFailedException("Invalid organisation, email or password");

		if (loginAttemptService.isLocked(email)) {
			log.warn("Login rejected (too many recent failures): org={} email={}", organisationSlug, email);
			throw failure;
		}

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> logFailure(failure, organisationSlug, email));
		User user = userRepository.findByEmail(email).orElseThrow(() -> logFailure(failure, organisationSlug, email));

		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw logFailure(failure, organisationSlug, email);
		}

		if (user.getStatus() != UserStatus.ACTIVE) {
			throw logFailure(failure, organisationSlug, email);
		}

		// OrganisationMembership (and RefreshToken, issued below) are tenant-scoped
		// (@TenantId) - login is the one place that has to resolve *which* tenant before it
		// can touch tenant-scoped data at all, so TenantContext must be set here first, for
		// the whole rest of this method, or these calls silently filter to "no tenant".
		TenantContext.set(organisation.getId(), user.getId());
		try {
			boolean hasActiveMembership = membershipRepository.findByUserId(user.getId()).stream()
					.anyMatch(membership -> membership.getStatus() == MembershipStatus.ACTIVE);
			if (!hasActiveMembership) {
				throw logFailure(failure, organisationSlug, email);
			}

			JwtService.IssuedToken issued = jwtService.issue(user.getId(), organisation.getId());
			RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
			loginAttemptService.recordSuccess(email);
			log.info("Login succeeded: org={} userId={}", organisationSlug, user.getId());
			return new LoginResult(issued.token(), issued.expiresAt(), refresh.rawToken(), refresh.expiresAt(), user.getId(),
					organisation.getId());
		} finally {
			TenantContext.clear();
		}
	}

	/** Authenticated - caller already holds a valid access token for this exact userId, so
	 * (unlike login()) the "current password is wrong" message can be specific: there's no
	 * enumeration risk in telling someone their own password back to themselves.
	 *
	 * Deliberately IllegalStateException (-> 409, see ApiExceptionHandler), not
	 * AuthenticationFailedException (-> 401) - this is a business-rule rejection by an
	 * already-authenticated caller, the same shape as RoleService.changeStatus's
	 * "can't deactivate the system-default role" or PortalInvite.claim()'s
	 * "already claimed", not an authentication failure. web/src/api/client.ts treats *any*
	 * 401 on a request carrying a session as "the session itself is invalid" and clears it
	 * - a 401 here for a simple wrong-current-password typo would silently log the caller
	 * out and show a generic "session expired" instead of the real message, which is
	 * exactly the bug this codebase's own ApiExceptionHandler javadoc warns never to cause
	 * ("never issues one for a business rule"). */
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new IllegalStateException("Current password is incorrect");
		}
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		userRepository.save(user);
	}

	/**
	 * Public/unauthenticated, same shape as login() - a caller with an expired access
	 * token has no valid bearer token to carry the org, so the slug travels explicitly in
	 * the request body instead, exactly like PortalInviteService.claim(). Rotates on every
	 * call: the presented refresh token is revoked the moment a new pair is issued from it.
	 */
	public LoginResult refresh(String organisationSlug, String rawRefreshToken) {
		AuthenticationFailedException invalid = new AuthenticationFailedException("Invalid or expired refresh token");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalid);
		TenantContext.set(organisation.getId(), null);
		try {
			Long userId = refreshTokenService.consumeAndRevoke(rawRefreshToken).orElseThrow(() -> invalid);
			User user = userRepository.findById(userId).orElseThrow(() -> invalid);

			JwtService.IssuedToken issued = jwtService.issue(user.getId(), organisation.getId());
			RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
			log.info("Token refreshed: org={} userId={}", organisationSlug, user.getId());
			return new LoginResult(issued.token(), issued.expiresAt(), refresh.rawToken(), refresh.expiresAt(), user.getId(),
					organisation.getId());
		} finally {
			TenantContext.clear();
		}
	}

	/** The client-facing message is deliberately identical across every failure cause (see
	 * above), but internal logs can - and should - be more specific, so a real security
	 * review can tell a brute-force attempt on one org apart from a mistyped email. Logs
	 * the org slug and email, never the password. */
	private AuthenticationFailedException logFailure(AuthenticationFailedException failure, String organisationSlug, String email) {
		log.warn("Login failed: org={} email={}", organisationSlug, email);
		loginAttemptService.recordFailure(email);
		return failure;
	}
}
