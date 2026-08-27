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
 * Verification-token-on-create, deliberately non-blocking (the alternative the issue itself
 * offers: verification-required-before-first-login is not implemented - requiring
 * verification before login would lock out every user for however long email delivery
 * takes or fails). Now actually emailed via EmailDeliveryService (GitHub #105) rather than
 * only logged - the log line stays as a fallback trace, same "manual copy still works even
 * if delivery didn't" trust tier as StaffInviteService.
 */
@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final Duration VERIFICATION_TOKEN_VALIDITY = Duration.ofDays(3);

	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final UserRepository userRepository;
	private final OrganisationRepository organisationRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailDeliveryService emailDeliveryService;
	private final String frontendBaseUrl;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailVerificationService(EmailVerificationTokenRepository emailVerificationTokenRepository, UserRepository userRepository,
			OrganisationRepository organisationRepository, PasswordEncoder passwordEncoder, EmailDeliveryService emailDeliveryService,
			@Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.userRepository = userRepository;
		this.organisationRepository = organisationRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailDeliveryService = emailDeliveryService;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/** Caller must already have TenantContext set - same precondition as
	 * RefreshTokenService.issue(). */
	public void issue(Long userId) {
		String rawToken = generateToken();
		EmailVerificationToken token = emailVerificationTokenRepository
				.save(new EmailVerificationToken(userId, passwordEncoder.encode(rawToken), Instant.now().plus(VERIFICATION_TOKEN_VALIDITY)));
		// organisationSlug isn't passed in - only TenantContext's organisationId is
		// available to an admin-authenticated caller like UserController.create() - so it's
		// looked up here purely for the link, same shape as PasswordResetService's.
		String organisationSlug = organisationRepository.findById(TenantContext.getOrganisationId())
				.map(Organisation::getSlug)
				.orElse(null);
		log.info("Email verification issued: org={} userId={} link=/verify-email?org={}&token={} expiresAt={}", organisationSlug,
				userId, organisationSlug, rawToken, token.getExpiresAt());

		if (organisationSlug == null) {
			return;
		}
		String recipient = userRepository.findById(userId).map(User::getEmail).orElse(null);
		String link = frontendBaseUrl + "/verify-email?org=" + urlEncode(organisationSlug) + "&token=" + urlEncode(rawToken);
		String html = "<p>Verify your email address for <strong>" + organisationSlug + "</strong> on Operion.</p>"
				+ "<p><a href=\"" + link + "\">Verify email</a></p>"
				+ "<p>Or paste this link into your browser:<br>" + link + "</p>"
				+ "<p>This link expires in 3 days.</p>";
		emailDeliveryService.sendBestEffort(recipient, "Verify your email for " + organisationSlug, html);
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
