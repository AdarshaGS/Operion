package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.operion.common.SecretsGuard.Finding;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminPasswordBootstrap;
import com.operion.platform.PlatformAdminRepository;

/** SecretsGuard and PlatformAdminPasswordBootstrap against a real platform_admins table, constructed by hand as in PlatformAuthenticationServiceTest. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SecretsGuardStartupTest {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
	private static final String DEV_JWT = "dev-only-secret-change-me-before-any-real-deployment-32bytes-min";
	private static final String GOOD_A = "k8Jx2mQv9TzR4wLp7NcY1bHs6DfG3aEu";
	private static final String GOOD_B = "Zq5Wn8Rt2Yv6Bm9Kc3Lx7Hp4Jd1Gf0Sa";
	private static final String GOOD_C = "Pm4Xs7Vb2Nq9Lr6Tw3Ky8Hc5Jf1Dg0Ze";

	@Autowired
	private PlatformAdminRepository admins;

	private SecretsGuard guard(String mode, String jwt, String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profiles);
		return new SecretsGuard(environment, admins, ENCODER, mode, jwt, GOOD_B, GOOD_C, "db-Str0ng-pass", "db-Str0ng-pass",
				"reporting_ro", "rotated-Rep0rting", true);
	}

	private PlatformAdmin seededAdmin() {
		admins.deleteAll();
		return admins.save(new PlatformAdmin("Platform Admin", "admin@operion.platform",
				ENCODER.encode(PlatformAdminPasswordBootstrap.SEEDED_PASSWORD)));
	}

	@Test
	void productionRefusesToStartWithADevelopmentSecretAndNamesItWithoutPrintingIt() {
		admins.deleteAll();
		assertThatThrownBy(() -> guard("", DEV_JWT, "prod").run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Refusing to start")
				.hasMessageContaining("JWT_SECRET is still the built-in development value")
				.hasMessageNotContaining(DEV_JWT);
	}

	@Test
	void outsideProductionTheSameFindingOnlyWarns() {
		admins.deleteAll();
		SecretsGuard dev = guard("", DEV_JWT, "dev");
		assertThatCode(() -> dev.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
		assertThat(dev.report().enforced()).isFalse();
		assertThat(dev.report().problems()).extracting(Finding::setting).containsExactly("JWT_SECRET");
		assertThat(guard("enforce", DEV_JWT, "dev").report().enforced()).as("SECRETS_GUARD=enforce").isTrue();
		assertThat(guard("warn", DEV_JWT, "prod").report().enforced()).as("SECRETS_GUARD=warn").isFalse();
	}

	@Test
	void theSeededPlatformAdminPasswordBlocksProductionUntilPlatformAdminPasswordIsSet() {
		seededAdmin();
		assertThat(guard("", GOOD_A, "prod").report().problems()).extracting(Finding::setting).containsExactly("PLATFORM_ADMIN_PASSWORD");
		assertThatThrownBy(() -> guard("", GOOD_A, "prod").run(new DefaultApplicationArguments()))
				.hasMessageContaining("admin@operion.platform still has the seeded password");

		new PlatformAdminPasswordBootstrap(admins, ENCODER, "a-Real-Console-Passw0rd").run(new DefaultApplicationArguments());

		PlatformAdmin admin = admins.findByEmail("admin@operion.platform").orElseThrow();
		assertThat(ENCODER.matches("a-Real-Console-Passw0rd", admin.getPasswordHash())).isTrue();
		assertThat(guard("", GOOD_A, "prod").report().problems()).isEmpty();
		assertThatCode(() -> guard("", GOOD_A, "prod").run(new DefaultApplicationArguments())).doesNotThrowAnyException();
	}

	@Test
	void theBootstrapNeverOverwritesAPasswordThatWasAlreadyChanged() {
		admins.deleteAll();
		admins.save(new PlatformAdmin("Platform Admin", "admin@operion.platform", ENCODER.encode("chosen-by-the-operator")));
		new PlatformAdminPasswordBootstrap(admins, ENCODER, "a-Real-Console-Passw0rd").run(new DefaultApplicationArguments());
		assertThat(ENCODER.matches("chosen-by-the-operator", admins.findByEmail("admin@operion.platform").orElseThrow().getPasswordHash())).isTrue();
	}

	@Test
	void aTooShortBootstrapPasswordIsRefused() {
		seededAdmin();
		assertThatThrownBy(() -> new PlatformAdminPasswordBootstrap(admins, ENCODER, "short").run(new DefaultApplicationArguments()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 12 characters");
	}
}
