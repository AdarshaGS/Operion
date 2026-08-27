package com.operion.dashboard.api;

/** Feeds the dismissible "getting started" checklist on the Dashboard (#97) - a
 * guidance layer, not a gate. Each flag is a coarse, cheap-to-compute signal that the
 * corresponding setup step has been touched at all, not a precise completion audit. */
public record SetupChecklist(boolean structureConfigured, boolean rolesConfigured, boolean membersAdded,
		boolean industryDataAdded) {
}
