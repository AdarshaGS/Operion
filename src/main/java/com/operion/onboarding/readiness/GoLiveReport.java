package com.operion.onboarding.readiness;

import java.util.List;

/**
 * {@code ready} is true when nothing FAILs. {@code hiddenModules} are the modules switched off
 * for this organisation - listed so the report says what it deliberately did not check.
 */
public record GoLiveReport(boolean ready, List<Group> groups, List<String> hiddenModules) {

	/** {@code code} is an AppModule code, or ORGANISATION for the organisation-wide checks. */
	public record Group(String code, String label, ReadinessLevel level, boolean hasChecks, List<ReadinessFinding> findings) {
	}
}
