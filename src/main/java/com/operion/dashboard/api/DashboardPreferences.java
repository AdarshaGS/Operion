package com.operion.dashboard.api;

/** Per-user, permanent-once-set dismissal flags for the two onboarding widgets on the
 * Dashboard (Setup progress, Quick actions) - independent of each other and of
 * SetupChecklist completion. */
public record DashboardPreferences(boolean setupProgressDismissed, boolean quickActionsDismissed) {
}
