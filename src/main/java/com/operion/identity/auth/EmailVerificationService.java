package com.operion.identity.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.operion.common.TenantContext;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verification-token-on-create, deliberately non-blocking (the alternative the issue itself
 * offers: verification-required-before-first-login is not implemented - with no real mail
 * transport in this codebase, requiring verification before login would lock out every user
 * created in v1). Same "log the link instead of returning it" reasoning as
 * PasswordResetService for confirm(), which is public/unauthenticated; issue() is
 * admin-authenticated (called from UserController.create()) but the email belongs to the new
 * user, not the admin caller, so it's logged rather than handed back either way.
 */
@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final Duration VERIFICATION_TOKEN_VALIDITY = Duration.ofDays(3);

	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailVerificationService(EmailVerificationTokenRepository emailVerificationTokenRepository, UserRepository userRepository,
			OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder) {
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** Caller must already have TenantContext set - same precondition as
	 * RefreshTokenService.issue(). */
	public void issue(Long userId) {
		String rawToken = generateToken();
		EmailVerificationToken token = emailVerificationTokenRepository
				.save(new EmailVerificationToken(userId, passwordEncoder.encode(rawToken), Instant.now().plus(VERIFICATION_TOKEN_VALIDITY)));
		log.info("Email verification issued: userId={} link=/verify-email?token={} expiresAt={}", userId, rawToken,
				token.getExpiresAt());
	}

	public void confirm(String organisationSlug, String rawToken) {
		AuthenticationFailedException invalid = new AuthenticationFailedException("Invalid or expired verification token");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalid);
		TenantContext.set(organisation.getId(), null);
		try {
			EmailVerificationToken token = emailVerificationTokenRepository.findByConsumedFalse().stream()
					.filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
					.findFirst()
					.filter(EmailVerificationToken::isValid)
					.orElseThrow(() -> invalid);

			User user = userRepository.findById(token.getUserId()).orElseThrow(() -> invalid);
			user.setEmailVerifiedAt(Instant.now());
			userRepository.save(user);

			token.consume();
			emailVerificationTokenRepository.save(token);
		} finally {
			TenantContext.clear();
		}
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
