package com.operion.organisation;

import java.util.Map;
import java.util.Set;

/**
 * Fixed starter role set seeded at org provisioning, per
 * ai-context/erp-system-plan.md §1.5 - an app-level constant list, not a
 * nullable-organisation_id "template role" pattern.
 */
final class DefaultRoles {

	static final String ORG_ADMIN = "Org Admin";

	static final Map<String, Set<String>> NON_ADMIN_ROLES = Map.of(
			"Teacher", Set.of("STUDENT_VIEW", "ATTENDANCE_VIEW", "ATTENDANCE_MARK", "REPORT_VIEW"),
			"Accountant", Set.of("STUDENT_VIEW", "FEE_VIEW", "FEE_COLLECT", "REPORT_VIEW"),
			"Front Desk", Set.of("STUDENT_VIEW", "STUDENT_CREATE", "STUDENT_UPDATE"));

	private DefaultRoles() {
	}
}
