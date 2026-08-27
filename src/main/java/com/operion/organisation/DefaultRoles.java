package com.operion.organisation;

import java.util.Map;
import java.util.Set;

/**
 * Fixed starter role set seeded at org provisioning, per
 * ai-context/erp-system-plan.md §1.5 - an app-level constant list, not a
 * nullable-organisation_id "template role" pattern.
 */
final class DefaultRoles {

	static final String OWNER = "Owner";

	/** PortalInviteService (com.operion.parent) looks this role up by the same literal
	 * name - package-private here, so it can't share this constant directly, but a fresh
	 * org gets it seeded automatically, matching Teacher/Accountant/Front Desk below. An
	 * org provisioned before this role existed needs one created manually via Settings >
	 * Roles, same as any other default-role addition to this map. */
	static final String GUARDIAN = "Guardian";

	static final Map<String, Set<String>> NON_ADMIN_ROLES = Map.of(
			"Teacher", Set.of("STUDENT_VIEW", "ATTENDANCE_VIEW", "ATTENDANCE_MARK", "REPORT_VIEW"),
			"Accountant", Set.of("STUDENT_VIEW", "FEE_VIEW", "FEE_COLLECT", "REPORT_VIEW"),
			"Front Desk", Set.of("STUDENT_VIEW", "STUDENT_MANAGE"),
			GUARDIAN, Set.of("PARENT_PORTAL_ACCESS"));

	private DefaultRoles() {
	}
}
