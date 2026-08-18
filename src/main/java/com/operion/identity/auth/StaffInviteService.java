package com.operion.identity.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.identity.UserStatus;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin creates a login shell (User with a random, never-usable password and status
 * PENDING) and gets a claim token back to hand the new hire directly - same
 * "no email/SMS delivery, staff relays it however they already do" trust tier as
 * GuardianController.grantPortalAccess, since this is admin-authenticated rather than
 * public. claim() below is the public/unauthenticated half, same shape as
 * PortalInviteService.claim().
 */
@Service
public class StaffInviteService {

	private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

	private final StaffInviteRepository staffInviteRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final SecureRandom secureRandom = new SecureRandom();

	public StaffInviteService(StaffInviteRepository staffInviteRepository, UserRepository userRepository,
			OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			RefreshTokenService refreshTokenService) {
		this.staffInviteRepository = staffInviteRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	/** Staff-facing - runs with TenantContext already set by JwtAuthenticationInterceptor,
	 * same as PortalInviteService.issue(). */
	@Transactional
	public IssuedInvite issue(String email, String phone) {
		User user = new User(email, phone, passwordEncoder.encode(generateToken()));
		user.setStatus(UserStatus.PENDING);
		user = userRepository.save(user);

		String rawToken = generateToken();
		StaffInvite invite = staffInviteRepository
				.save(new StaffInvite(user.getId(), passwordEncoder.encode(rawToken), Instant.now().plus(INVITE_VALIDITY)));
		return new IssuedInvite(user.getId(), invite.getId(), rawToken, invite.getExpiresAt());
	}

	/** Public/unauthenticated, same shape as PortalInviteService.claim() - no valid access
	 * token exists yet, so the org slug travels explicitly in the request body. */
	public LoginResult claim(String organisationSlug, String rawToken, String password) {
		AuthenticationFailedException invalidInvite = new AuthenticationFailedException("Invalid or expired invite");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalidInvite);
		TenantContext.set(organisation.getId(), null);
		try {
			StaffInvite invite = staffInviteRepository.findByStatus(StaffInviteStatus.PENDING).stream()
					.filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
					.findFirst()
					.filter(candidate -> candidate.getExpiresAt().isAfter(Instant.now()))
					.orElseThrow(() -> invalidInvite);

			User user = userRepository.findById(invite.getUserId()).orElseThrow(() -> invalidInvite);
			user.setPasswordHash(passwordEncoder.encode(password));
			user.setStatus(UserStatus.ACTIVE);
			userRepository.save(user);

			invite.claim();
			staffInviteRepository.save(invite);

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

	public record IssuedInvite(Long userId, Long inviteId, String rawToken, Instant expiresAt) {
	}
}
