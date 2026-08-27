package com.operion.organisation;

/**
 * Org provisioning seeds only the Owner - no industry-specific roles (Teacher/Accountant/
 * etc, removed per GitHub #92). An app-level constant rather than a nullable-
 * organisation_id "template role" row, per ai-context/erp-system-plan.md §1.5.
 */
final class DefaultRoles {

	static final String OWNER = "Owner";

	private DefaultRoles() {
	}
}
