package com.operion.platform.crm;

/**
 * No enforced transition map between these - same convention as
 * {@link com.operion.organisation.OrganisationStatus}: a platform admin can move a lead to
 * any stage at any time. Declaration order is the pipeline order the console displays.
 */
public enum LeadStage {
	NEW,
	CONTACTED,
	DEMO_SCHEDULED,
	PROPOSAL,
	WON,
	LOST;
}
