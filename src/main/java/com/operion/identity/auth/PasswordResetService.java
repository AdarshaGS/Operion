package com.operion.identity.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.identity.User;
import com.operion.identity.UserRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Public/unauthenticated forgot-password flow. Unlike StaffInviteService.issue() (an
 * admin-authenticated caller issuing a token for someone else, safe to hand back directly -
 * see GuardianController.grantPortalAccess for the same trust tier), requestReset() is
 * reachable by anyone who knows an email address. Returning the raw token in the response
 * would let anyone take over any account just by knowing its address, so the link is only
 * ever emailed to the account's own address (EmailDeliveryService, same as verification).
 * The raw token is logged only when app.dev.log-auth-tokens is on (local/automation stacks
 * that have no mail provider) - never by default, since log access must not equal account
 * access (SEC-M2). The response is always the same generic ack regardless of whether the org/email matched,
 * mirroring AuthenticationService.login()'s anti-enumeration stance.
 *
 * Tokens are stored as a SHA-256 hash and found by an indexed equality lookup (they are 256-bit
 * random, so a fast hash is enough - see TokenHasher), instead of bcrypt-comparing every live token.
 * Confirming is single-use via one conditional UPDATE, so two concurrent confirms cannot both win; a
 * new request invalidates the user's earlier unused tokens; and a completed reset revokes the user's
 * refresh tokens and (through User.setPasswordHash) every access token issued before it.
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final Duration RESET_TOKEN_VALIDITY = Duration.ofHours(1);

	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final EmailDeliveryService emailDeliveryService;
	private final String frontendBaseUrl;
	private final boolean logRawTokens;
	private final SecureRandom secureRandom = new SecureRandom();

	public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository, UserRepository userRepository,
			OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
			EmailDeliveryService emailDeliveryService,
			@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
			@Value("${app.dev.log-auth-tokens:false}") boolean logRawTokens) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.emailDeliveryService = emailDeliveryService;
		this.frontendBaseUrl = frontendBaseUrl;
		this.logRawTokens = logRawTokens;
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
			// Only the newest link works: an earlier unused one (say, from a leaked inbox) dies here.
			passwordResetTokenRepository.consumeAllForUser(user.getId());
			String rawToken = generateToken();
			PasswordResetToken token = passwordResetTokenRepository
					.save(new PasswordResetToken(user.getId(), TokenHasher.sha256Hex(rawToken), Instant.now().plus(RESET_TOKEN_VALIDITY)));
			// Never returned to the HTTP caller - see class javadoc.
			if (logRawTokens) {
				log.info("Password reset requested: org={} userId={} link=/reset-password?org={}&token={} expiresAt={}",
						organisationSlug, user.getId(), organisationSlug, rawToken, token.getExpiresAt());
			} else {
				log.info("Password reset requested: org={} userId={} expiresAt={}", organisationSlug, user.getId(), token.getExpiresAt());
			}
			String link = frontendBaseUrl + "/reset-password?org=" + urlEncode(organisationSlug) + "&token=" + urlEncode(rawToken);
			String html = "<p>A password reset was requested for your account at <strong>" + organisationSlug + "</strong> on Operion.</p>"
					+ "<p><a href=\"" + link + "\">Choose a new password</a></p>"
					+ "<p>Or paste this link into your browser:<br>" + link + "</p>"
					+ "<p>This link expires in 1 hour. If you didn't ask for this, you can ignore this email.</p>";
			emailDeliveryService.sendBestEffort(user.getEmail(), "Reset your Operion password", html);
		} finally {
			TenantContext.clear();
		}
	}

	public void confirmReset(String organisationSlug, String rawToken, String newPassword) {
		AuthenticationFailedException invalid = new AuthenticationFailedException("Invalid or expired reset token");

		Organisation organisation = organisationRepository.findBySlug(organisationSlug).orElseThrow(() -> invalid);
		TenantContext.set(organisation.getId(), null);
		try {
			PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
					.orElseThrow(() -> invalid);
			// Atomic single-use gate: only the caller whose UPDATE flips the row (1 row) proceeds; a
			// replay, a concurrent twin, or an expired token matches 0 rows.
			if (passwordResetTokenRepository.consumeIfValid(token.getId(), Instant.now()) != 1) {
				throw invalid;
			}

			User user = userRepository.findById(token.getUserId()).orElseThrow(() -> invalid);
			user.setPasswordHash(passwordEncoder.encode(newPassword));
			userRepository.save(user);

			// Any other reset link still outstanding for this user is now moot, and every session
			// that predates the reset must go: refresh tokens here, access tokens via credentialVersion.
			passwordResetTokenRepository.consumeAllForUser(user.getId());
			refreshTokenService.revokeAllForUser(user.getId());
		} finally {
			TenantContext.clear();
		}
	}

	private String generateToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
