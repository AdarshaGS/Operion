package com.operion.onboarding.readiness;

import java.util.List;

import com.operion.modules.AppModule;

/**
 * A module's contribution to the go-live report: what must be configured and loaded before
 * the module is worth switching on for users. Each module adds its own bean; nothing central
 * changes when a module is added. Only run for modules that are enabled for the organisation.
 * Runs inside the tenant context, so repositories already see only this organisation's rows.
 */
public interface ReadinessCheck {

	/** The module this check belongs to, or null for organisation-wide checks that apply whatever is enabled. */
	AppModule module();

	List<ReadinessFinding> check();
}
