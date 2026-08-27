package com.operion.common;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the core/vertical package boundary documented in
 * ai-context/platform-boundaries.md - core stays industry-neutral and must never depend
 * on the School vertical, so a future PR can't silently reintroduce the coupling that
 * OrganisationConfiguration.school_start_time was (see V31 migration).
 */
class ArchitectureBoundaryTest {

	private static final String[] CORE_PACKAGES = {
			"com.operion.organisation..", "com.operion.identity..", "com.operion.authorization..", "com.operion.audit.."
	};

	private static final String[] VERTICAL_PACKAGES = {
			"com.operion.academic..", "com.operion.student..", "com.operion.parent..", "com.operion.attendance..",
			"com.operion.finance..", "com.operion.examination..", "com.operion.communication..", "com.operion.library..",
			"com.operion.transport..", "com.operion.hr.."
	};

	@Test
	void coreNeverDependsOnVertical() {
		var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.operion");

		ArchRule rule = noClasses().that().resideInAnyPackage(CORE_PACKAGES)
				.should().dependOnClassesThat().resideInAnyPackage(VERTICAL_PACKAGES);

		rule.check(classes);
	}
}
