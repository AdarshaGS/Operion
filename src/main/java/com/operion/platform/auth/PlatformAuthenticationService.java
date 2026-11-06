package com.operion.platform.auth;

import java.time.Instant;

import com.operion.identity.auth.LoginAttemptService;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import com.operion.platform.PlatformAdminStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthenticationService {

	/** Platform admins share the staff login-attempt store under this scope. Organisation ids start at 1, so a
	 * platform key ("0:email") can never collide with a school account's - and a platform admin's lockout is
	 * independent of any staff account with the same email. */
	static final long PLATFORM_ADMIN_SCOPE = 0L;

	private static final Logger log = LoggerFactory.getLogger(PlatformAuthenticationService.class);

	private final PlatformAdminRepository platformAdminRepository;
	private final PasswordEncoder passwordEncoder;
	private final PlatformJwtService platformJwtService;
	private final LoginAttemptService loginAttemptService;

	public PlatformAuthenticationService(PlatformAdminRepository platformAdminRepository, PasswordEncoder passwordEncoder,
			PlatformJwtService platformJwtService, LoginAttemptService loginAttemptService) {
		this.platformAdminRepository = platformAdminRepository;
		this.passwordEncoder = passwordEncoder;
		this.platformJwtService = platformJwtService;
		this.loginAttemptService = loginAttemptService;
	}

	public PlatformLoginResult login(String email, String rawPassword) {
		// Deliberately the same error for "no such email", "wrong password" and "locked out" - same
		// enumeration-prevention reasoning as AuthenticationService.login.
		PlatformAuthenticationFailedException failure = new PlatformAuthenticationFailedException("Invalid email or password");

		// Checked before the password, so a locked account can't be used as a password oracle.
		if (loginAttemptService.isLocked(PLATFORM_ADMIN_SCOPE, email)) {
			log.warn("Platform login rejected (too many recent failures): email={}", email);
			throw failure;
		}

		PlatformAdmin admin = platformAdminRepository.findByEmail(email).orElse(null);
		if (admin == null || !passwordEncoder.matches(rawPassword, admin.getPasswordHash())
				|| admin.getStatus() != PlatformAdminStatus.ACTIVE) {
			log.warn("Platform login failed: email={}", email);
			loginAttemptService.recordFailure(PLATFORM_ADMIN_SCOPE, email);
			throw failure;
		}

		PlatformJwtService.IssuedToken issued = platformJwtService.issue(admin.getId());
		loginAttemptService.recordSuccess(PLATFORM_ADMIN_SCOPE, email);
		return new PlatformLoginResult(issued.token(), issued.expiresAt(), admin.getId());
	}

	public record PlatformLoginResult(String token, Instant expiresAt, Long platformAdminId) {
	}
}
