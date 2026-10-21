package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.operion.common.SecretsGuard.Finding;
import com.operion.common.SecretsGuard.Settings;

class SecretsGuardTest {

	private static final String KEY_A = "k8Jx2mQv9TzR4wLp7NcY1bHs6DfG3aEu";
	private static final String KEY_B = "Zq5Wn8Rt2Yv6Bm9Kc3Lx7Hp4Jd1Gf0Sa";
	private static final String KEY_C = "Pm4Xs7Vb2Nq9Lr6Tw3Ky8Hc5Jf1Dg0Ze";

	private static Settings good() {
		return new Settings(KEY_A, KEY_B, KEY_C, "db-Str0ng-pass", "db-Str0ng-pass", "reporting_ro", "rotated-Rep0rting", true);
	}

	private static List<String> settingsFlagged(Settings s) {
		return SecretsGuard.check(s).stream().map(Finding::setting).toList();
	}

	@Test
	void aProperlyConfiguredDeploymentHasNoFindings() {
		assertThat(SecretsGuard.check(good())).isEmpty();
	}

	@Test
	void theShippedDevelopmentDefaultsAreAllFlaggedByName() {
		Settings dev = new Settings("dev-only-secret-change-me-before-any-real-deployment-32bytes-min",
				"dev-only-platform-secret-change-me-before-any-real-deployment-32b",
				"dev-only-integration-secret-change-me-before-any-real-deployment", "", "", "root", "mysql", false);
		assertThat(settingsFlagged(dev)).containsExactly("JWT_SECRET", "PLATFORM_JWT_SECRET", "INTEGRATION_SECRET_KEY",
				"DB_PASSWORD", "REPORTING_DB_USERNAME", "REPORTING_DB_PASSWORD");
	}

	@Test
	void findingsNameTheSettingButNeverContainTheValue() {
		Settings leaky = new Settings("short-secret-xyz", KEY_B, KEY_C, "db-Str0ng-pass", "db-Str0ng-pass", "reporting_ro",
				"changeme-reporting-ro", true);
		List<Finding> findings = SecretsGuard.check(leaky);
		assertThat(findings).extracting(Finding::setting).containsExactly("JWT_SECRET", "REPORTING_DB_PASSWORD");
		assertThat(findings).extracting(Finding::problem).noneMatch(p -> p.contains("short-secret-xyz"))
				.anyMatch(p -> p.contains("shorter than 32 bytes"));
	}

	@Test
	void theTwoTokenPlanesMustNotShareAKey() {
		Settings shared = new Settings(KEY_A, KEY_A, KEY_C, "db-Str0ng-pass", "db-Str0ng-pass", "reporting_ro", "rotated-Rep0rting", true);
		assertThat(settingsFlagged(shared)).containsExactly("PLATFORM_JWT_SECRET");
	}

	@Test
	void aMissingKeyIsReportedAsNotSet() {
		Settings missing = new Settings(KEY_A, KEY_B, " ", "db-Str0ng-pass", "db-Str0ng-pass", "reporting_ro", "rotated-Rep0rting", true);
		assertThat(SecretsGuard.check(missing)).containsExactly(new Finding("INTEGRATION_SECRET_KEY", "is not set"));
	}

	@Test
	void aSeparatelySetWeakPlatformDatabasePasswordIsReportedOnItsOwn() {
		Settings weakPlatform = new Settings(KEY_A, KEY_B, KEY_C, "db-Str0ng-pass", "root", "reporting_ro", "rotated-Rep0rting", true);
		assertThat(settingsFlagged(weakPlatform)).containsExactly("PLATFORM_DB_PASSWORD");
	}
}
