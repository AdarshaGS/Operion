package com.operion.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StructureConfiguredTest {

	@Test
	void completeWithProfileContactTimezoneAndCurrentYear() {
		assertThat(DashboardController.isStructureConfigured("Owner", "owner@school.test", "Asia/Kolkata", true)).isTrue();
	}

	@Test
	void incompleteWithoutCurrentAcademicYear() {
		assertThat(DashboardController.isStructureConfigured("Owner", "owner@school.test", "Asia/Kolkata", false)).isFalse();
	}

	@Test
	void incompleteWithoutProfileContact() {
		assertThat(DashboardController.isStructureConfigured(null, "owner@school.test", "Asia/Kolkata", true)).isFalse();
		assertThat(DashboardController.isStructureConfigured("Owner", " ", "Asia/Kolkata", true)).isFalse();
	}

	@Test
	void incompleteWithoutTimezone() {
		assertThat(DashboardController.isStructureConfigured("Owner", "owner@school.test", null, true)).isFalse();
	}
}
