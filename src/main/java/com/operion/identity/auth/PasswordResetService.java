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
 * Public/unauthenticated forgot-password flow. Unlike StaffInviteService.issue() (an
 * admin-authenticated caller issuing a token for someone else, safe to hand back directly -
 * see GuardianController.grantPortalAccess for the same trust tier), requestReset() is
 * reachable by anyone who knows an email address. There is no email-sending infrastructure
 * in this codebase yet, but returning the raw token in the response here - instead of
 * emailing it - would let anyone take over any account just by knowing its address. So the
 * token is logged server-side only (a stand-in for "the email that would be sent") and the
 * response is always the same generic ack regardless of whether the org/email matched,
 * mirroring AuthenticationService.login()'s anti-enumeration stance.
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final Duration RESET_TOKEN_VALIDITY = Duration.ofHours(1);

	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository, UserRepository userRepository,
			OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public void requestReset(String organisationSlug, String email) {
		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElse(null);
		if (organisation == null) {
			return;
		}
		TenantContext.set(organisation.getId(), null);
		try {
			User user = userRepository.findByEmail(email).orElse(null);
			if (user == null) {
				return;
			}
			String rawToken = generateToken();
			PasswordResetToken token = passwordResetTokenRepository
					.save(new PasswordResetToken(user.getId(), passwordEncoder.encode(rawToken), Instant.now().plus(RESET_TOKEN_VALIDITY)));
			// Stands in for the email that would otherwise be sent - see class javadoc. Never
			// returned to the HTTP caller.
			log.info("Password reset requested: org={} userId={} link=/reset-password?org={}&token={} expiresAt={}",
					organisationSlug, user.getId(), organisationSlug, rawToken, token.getExpiresAt());
		} finally {
			TenantContext.clear();
		}
	}

	public void confirmReset(String organisationSlug, String rawToken, String newPassword) {
		AuthenticationFailedException invalid = new AuthenticationFailedException("Invalid or expired reset token");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalid);
		TenantContext.set(organisation.getId(), null);
		try {
			PasswordResetToken token = passwordResetTokenRepository.findByConsumedFalse().stream()
					.filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
					.findFirst()
					.filter(PasswordResetToken::isValid)
					.orElseThrow(() -> invalid);

			User user = userRepository.findById(token.getUserId()).orElseThrow(() -> invalid);
			user.setPasswordHash(passwordEncoder.encode(newPassword));
			userRepository.save(user);

			token.consume();
			passwordResetTokenRepository.save(token);
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
