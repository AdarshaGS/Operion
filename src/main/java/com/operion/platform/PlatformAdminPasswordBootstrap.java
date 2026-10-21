package com.operion.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.operion.common.persistence.PlatformTransactional;

/**
 * The platform baseline seeds admin@operion.platform with the published password {@value #SEEDED_PASSWORD}, and there
 * is no screen to change it. When PLATFORM_ADMIN_PASSWORD is set, every platform admin still on that seeded password
 * is moved to it at startup; an admin whose password was already changed is never touched, so leaving the variable set
 * is harmless. Runs before {@link com.operion.common.SecretsGuard}, which refuses to start production while any admin
 * still has the seeded password.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformAdminPasswordBootstrap implements ApplicationRunner {

	public static final String SEEDED_PASSWORD = "ChangeMe123!";
	static final int MIN_LENGTH = 12;

	private static final Logger log = LoggerFactory.getLogger(PlatformAdminPasswordBootstrap.class);

	private final PlatformAdminRepository admins;
	private final PasswordEncoder passwordEncoder;
	private final String newPassword;

	public PlatformAdminPasswordBootstrap(PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
			@Value("${app.platform.admin-password:}") String newPassword) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.newPassword = newPassword;
	}

	@Override
	@PlatformTransactional
	public void run(ApplicationArguments args) {
		if (newPassword.isBlank()) {
			return;
		}
		if (newPassword.length() < MIN_LENGTH || newPassword.equals(SEEDED_PASSWORD)) {
			throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD must be at least " + MIN_LENGTH
					+ " characters and not the seeded password");
		}
		for (PlatformAdmin admin : admins.findAll()) {
			if (passwordEncoder.matches(SEEDED_PASSWORD, admin.getPasswordHash())) {
				admin.changePassword(passwordEncoder.encode(newPassword));
				admins.save(admin);
				log.info("Platform admin {} moved off the seeded password (PLATFORM_ADMIN_PASSWORD)", admin.getEmail());
			}
		}
	}
}
