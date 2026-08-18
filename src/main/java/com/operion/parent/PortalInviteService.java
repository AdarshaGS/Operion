package com.operion.parent;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.operion.authorization.MembershipService;
import com.operion.authorization.Role;
import com.operion.authorization.RoleRepository;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.identity.auth.JwtService;
import com.operion.identity.auth.LoginResult;
import com.operion.identity.auth.RefreshTokenService;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a Guardian's existing Person into a real login, without a parallel identity
 * system - reuses User/OrganisationMembership/Role exactly like staff onboarding
 * (UserController's "Add user" flow), just starting from a Person that already exists
 * instead of a fresh one. See ai-context/mobile-app-context.md for why this needed a
 * real design pass before any portal/mobile screens could be built.
 */
@Service
public class PortalInviteService {

	private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

	/** Matches DefaultRoles.GUARDIAN in com.operion.organisation - duplicated as a literal
	 * rather than shared, since that class is deliberately package-private to its own
	 * module (only OrganisationService uses it directly). An org provisioned before this
	 * role existed has none by this name yet - claim() fails with a clear message rather
	 * than silently doing nothing, see below. */
	private static final String GUARDIAN_ROLE_NAME = "Guardian";

	private final PortalInviteRepository portalInviteRepository;
	private final GuardianRepository guardianRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final OrganisationRepository organisationRepository;
	private final MembershipService membershipService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final SecureRandom secureRandom = new SecureRandom();

	public PortalInviteService(PortalInviteRepository portalInviteRepository, GuardianRepository guardianRepository,
			UserRepository userRepository, RoleRepository roleRepository, OrganisationRepository organisationRepository,
			MembershipService membershipService, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService) {
		this.portalInviteRepository = portalInviteRepository;
		this.guardianRepository = guardianRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.organisationRepository = organisationRepository;
		this.membershipService = membershipService;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	/** Staff-facing - runs with TenantContext already set by JwtAuthenticationInterceptor,
	 * so this can be a normal single @Transactional method unlike claim() below. */
	@Transactional
	public IssuedInvite issue(Long guardianId) {
		Guardian guardian = guardianRepository.findById(guardianId)
				.orElseThrow(() -> new IllegalArgumentException("No guardian with id " + guardianId));
		String rawToken = generateToken();
		PortalInvite invite = portalInviteRepository.save(
				new PortalInvite(guardian.getPerson(), passwordEncoder.encode(rawToken), Instant.now().plus(INVITE_VALIDITY)));
		return new IssuedInvite(invite.getId(), rawToken, invite.getExpiresAt());
	}

	/**
	 * Public/unauthenticated, like AuthenticationService.login() - a parent claiming an
	 * invite has no token yet, so TenantContext can't be populated by the interceptor.
	 * Same shape as login() for the same reason: resolve the org from the slug first,
	 * set TenantContext before any tenant-scoped repository call, clear it in finally.
	 * Deliberately not @Transactional for the same reason OrganisationService.provision()
	 * and BillingService.generateInvoice() aren't - the tenant identifier resolves once
	 * per session, so TenantContext must already be set before any wrapping transaction
	 * would open one.
	 */
	public LoginResult claim(String organisationSlug, String rawToken, String password) {
		AuthenticationFailedException invalidInvite = new AuthenticationFailedException("Invalid or expired invite");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalidInvite);
		TenantContext.set(organisation.getId(), null);
		try {
			PortalInvite invite = portalInviteRepository.findByStatus(PortalInviteStatus.PENDING).stream()
					.filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
					.findFirst()
					.filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
					.orElseThrow(() -> invalidInvite);

			// A guardian who already has a User from another context (e.g. also staff
			// somewhere) is reused as-is rather than having their existing password
			// silently overwritten - the password argument only takes effect for a
			// brand-new User.
			Person person = invite.getPerson();
			User user = userRepository.findByEmail(person.getEmail())
					.orElseGet(() -> userRepository.save(new User(person.getEmail(), person.getPhone(), passwordEncoder.encode(password))));

			Role guardianRole = roleRepository.findByName(GUARDIAN_ROLE_NAME)
					.orElseThrow(() -> new IllegalStateException(
							"No Guardian role configured for this organisation - ask an Org Admin to create one in Settings"));
			membershipService.grant(user.getId(), person.getId(), guardianRole.getId(), null);

			invite.claim();
			portalInviteRepository.save(invite);

			JwtService.IssuedToken issued = jwtService.issue(user.getId(), organisation.getId());
			RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
			return new LoginResult(issued.token(), issued.expiresAt(), refresh.rawToken(), refresh.expiresAt(), user.getId(),
					organisation.getId());
		} finally {
			TenantContext.clear();
		}
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record IssuedInvite(Long inviteId, String rawToken, Instant expiresAt) {
	}
}
