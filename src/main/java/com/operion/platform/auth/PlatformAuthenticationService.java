package com.operion.platform.auth;

import java.time.Instant;

import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import com.operion.platform.PlatformAdminStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuthenticationService {

	private final PlatformAdminRepository platformAdminRepository;
	private final PasswordEncoder passwordEncoder;
	private final PlatformJwtService platformJwtService;

	public PlatformAuthenticationService(PlatformAdminRepository platformAdminRepository, PasswordEncoder passwordEncoder,
			PlatformJwtService platformJwtService) {
		this.platformAdminRepository = platformAdminRepository;
		this.passwordEncoder = passwordEncoder;
		this.platformJwtService = platformJwtService;
	}

	public PlatformLoginResult login(String email, String rawPassword) {
		// Deliberately the same error for "no such email" and "wrong password" - same
		// enumeration-prevention reasoning as AuthenticationService.login.
		PlatformAuthenticationFailedException failure = new PlatformAuthenticationFailedException("Invalid email or password");

		PlatformAdmin admin = platformAdminRepository.findByEmail(email).orElseThrow(() -> failure);
		if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
			throw failure;
		}
		if (admin.getStatus() != PlatformAdminStatus.ACTIVE) {
			throw failure;
		}

		PlatformJwtService.IssuedToken issued = platformJwtService.issue(admin.getId());
		return new PlatformLoginResult(issued.token(), issued.expiresAt(), admin.getId());
	}

	public record PlatformLoginResult(String token, Instant expiresAt, Long platformAdminId) {
	}
}
