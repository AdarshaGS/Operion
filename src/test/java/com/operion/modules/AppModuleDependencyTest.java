package com.operion.modules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class AppModuleDependencyTest {

	@Test
	void switchingOnAModulePullsInEverythingItNeedsTransitively() {
		assertThat(AppModule.withDependencies(EnumSet.of(AppModule.TRANSPORT)))
				.containsExactlyInAnyOrder(AppModule.TRANSPORT, AppModule.STUDENTS, AppModule.FEES);
		assertThat(AppModule.withDependencies(EnumSet.of(AppModule.LEARNING)))
				.contains(AppModule.STUDENTS, AppModule.ACADEMICS, AppModule.EXAMINATIONS);
		assertThat(AppModule.withDependencies(EnumSet.of(AppModule.PAYROLL))).containsExactlyInAnyOrder(AppModule.PAYROLL, AppModule.HR);
	}

	@Test
	void noModuleRequiresItselfDirectlyOrThroughACycle() {
		for (AppModule module : AppModule.values()) {
			for (AppModule required : module.requires()) {
				assertThat(AppModule.withDependencies(EnumSet.of(required))).as("%s requires %s", module, required).doesNotContain(module);
			}
		}
	}

	@Test
	void dependentsAreTheInverseOfRequires() {
		assertThat(AppModule.FEES.dependents()).contains(AppModule.TRANSPORT);
		assertThat(AppModule.HR.dependents()).containsExactlyInAnyOrder(AppModule.PAYROLL, AppModule.PERFORMANCE);
		assertThat(AppModule.SALES.dependents()).isEmpty();
	}

	@Test
	void lookupByCodeIsLenient() {
		assertThat(AppModule.fromCode("FEES")).contains(AppModule.FEES);
		assertThat(AppModule.fromCode("NOT_A_MODULE")).isEmpty();
	}
}
