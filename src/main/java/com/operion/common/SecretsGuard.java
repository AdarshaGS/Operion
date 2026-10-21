package com.operion.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.operion.common.persistence.PlatformTransactional;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminPasswordBootstrap;
import com.operion.platform.PlatformAdminRepository;

/**
 * Refuses to start production while any secret is still a value published in this repository (the dev-only JWT and
 * integration keys, the reporting_ro password from the school baseline, the seeded platform admin password), empty, or
 * too short. With the prod profile (or SECRETS_GUARD=enforce) a finding stops startup; otherwise each one is logged as a
 * warning so local stacks keep working. Findings name the environment variable to set, never the value. The same report
 * is shown on the platform console's System health page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecretsGuard implements ApplicationRunner {

	/** Shortest key accepted for HMAC signing and the integration cipher. */
	static final int MIN_KEY_BYTES = 32;

	private static final Set<String> WEAK_DB_PASSWORDS = Set.of("", "root", "mysql", "password", "operion", "changeme");
	private static final Set<String> PUBLISHED_REPORTING_PASSWORDS = Set.of("mysql", "changeme-reporting-ro");

	private static final Logger log = LoggerFactory.getLogger(SecretsGuard.class);

	public record Finding(String setting, String problem) {
	}

	public record Report(boolean enforced, List<Finding> problems, List<String> warnings) {
	}

	/** The values checked, gathered in one place so the rules can be tested without a Spring context. */
	record Settings(String jwtSecret, String platformJwtSecret, String integrationKey, String dbPassword,
			String platformDbPassword, String reportingUsername, String reportingPassword, boolean backupsScheduled) {
	}

	private final Environment environment;
	private final PlatformAdminRepository admins;
	private final PasswordEncoder passwordEncoder;
	private final String mode;
	private final Settings settings;

	public SecretsGuard(Environment environment, PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
			@Value("${app.security.secrets-guard:}") String mode,
			@Value("${app.jwt.secret}") String jwtSecret,
			@Value("${app.jwt.platform-secret}") String platformJwtSecret,
			@Value("${app.integration.secret-key}") String integrationKey,
			@Value("${spring.datasource.password:}") String dbPassword,
			@Value("${app.platform.datasource.password:}") String platformDbPassword,
			@Value("${app.reporting.datasource.username:}") String reportingUsername,
			@Value("${app.reporting.datasource.password:}") String reportingPassword,
			@Value("${app.backup.enabled:false}") boolean backupsScheduled) {
		this.environment = environment;
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.mode = mode.trim().toLowerCase(Locale.ROOT);
		this.settings = new Settings(jwtSecret, platformJwtSecret, integrationKey, dbPassword, platformDbPassword,
				reportingUsername, reportingPassword, backupsScheduled);
	}

	@Override
	@PlatformTransactional
	public void run(ApplicationArguments args) {
		Report report = report();
		if (report.problems().isEmpty()) {
			log.info("Secrets check passed");
		} else if (report.enforced()) {
			StringBuilder message = new StringBuilder("Refusing to start: ").append(report.problems().size())
					.append(" secret(s) still need to be set for production:");
			report.problems().forEach(f -> message.append("\n  - ").append(f.setting()).append(' ').append(f.problem()));
			message.append("\nSee docs/setup/README.md (Production secrets).");
			throw new IllegalStateException(message.toString());
		} else {
			report.problems().forEach(f -> log.warn("Secrets check: {} {} - production would refuse to start", f.setting(), f.problem()));
		}
		report.warnings().forEach(w -> log.warn("Secrets check: {}", w));
	}

	@PlatformTransactional
	public Report report() {
		List<Finding> problems = new ArrayList<>(check(settings));
		problems.addAll(seededAdminPasswords());
		List<String> warnings = settings.backupsScheduled() ? List.of()
				: List.of("Scheduled backups are off - set BACKUP_ENABLED=true");
		return new Report(enforced(), List.copyOf(problems), warnings);
	}

	boolean enforced() {
		return switch (mode) {
			case "enforce" -> true;
			case "warn" -> false;
			default -> environment.acceptsProfiles(Profiles.of("prod"));
		};
	}

	static List<Finding> check(Settings s) {
		List<Finding> problems = new ArrayList<>();
		checkKey(problems, "JWT_SECRET", s.jwtSecret());
		checkKey(problems, "PLATFORM_JWT_SECRET", s.platformJwtSecret());
		if (!blank(s.jwtSecret()) && s.jwtSecret().equals(s.platformJwtSecret())) {
			problems.add(new Finding("PLATFORM_JWT_SECRET", "is the same as JWT_SECRET; staff and platform tokens must be signed with different keys"));
		}
		checkKey(problems, "INTEGRATION_SECRET_KEY", s.integrationKey());

		boolean weakDb = WEAK_DB_PASSWORDS.contains(nullToEmpty(s.dbPassword()).toLowerCase(Locale.ROOT));
		if (weakDb) {
			problems.add(new Finding("DB_PASSWORD", "is empty or a well-known default"));
		}
		// PLATFORM_DB_PASSWORD falls back to DB_PASSWORD; only report it separately when it was set on its own.
		if (!nullToEmpty(s.platformDbPassword()).equals(nullToEmpty(s.dbPassword()))
				&& WEAK_DB_PASSWORDS.contains(nullToEmpty(s.platformDbPassword()).toLowerCase(Locale.ROOT))) {
			problems.add(new Finding("PLATFORM_DB_PASSWORD", "is empty or a well-known default"));
		}

		if ("root".equalsIgnoreCase(nullToEmpty(s.reportingUsername()).trim())) {
			problems.add(new Finding("REPORTING_DB_USERNAME", "is root; saved-report SQL must run as the SELECT-only reporting_ro user"));
		}
		if (blank(s.reportingPassword()) || PUBLISHED_REPORTING_PASSWORDS.contains(s.reportingPassword())) {
			problems.add(new Finding("REPORTING_DB_PASSWORD",
					"is empty or the published default; rotate it with ALTER USER 'reporting_ro'@'%' IDENTIFIED BY '...'"));
		}
		return problems;
	}

	private List<Finding> seededAdminPasswords() {
		List<Finding> problems = new ArrayList<>();
		try {
			for (PlatformAdmin admin : admins.findAll()) {
				if (passwordEncoder.matches(PlatformAdminPasswordBootstrap.SEEDED_PASSWORD, admin.getPasswordHash())) {
					problems.add(new Finding("PLATFORM_ADMIN_PASSWORD",
							"is not set and platform admin " + admin.getEmail() + " still has the seeded password"));
				}
			}
		} catch (RuntimeException e) {
			log.warn("Secrets check: could not read platform admins to check for the seeded password", e);
		}
		return problems;
	}

	private static void checkKey(List<Finding> problems, String setting, String value) {
		if (blank(value)) {
			problems.add(new Finding(setting, "is not set"));
			return;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.contains("dev-only") || lower.contains("test-only") || lower.contains("change-me") || lower.contains("changeme")) {
			problems.add(new Finding(setting, "is still the built-in development value"));
		} else if (value.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
			problems.add(new Finding(setting, "is shorter than " + MIN_KEY_BYTES + " bytes"));
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
