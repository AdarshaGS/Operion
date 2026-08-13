package com.operion.organisation;

/**
 * No enforced state machine between these - a platform admin (or an org's own admin,
 * via ORGANISATION_MANAGE) can move an organisation to any status at any time, per
 * https://github.com/AdarshaGS/Operion/issues/21.
 */
public enum OrganisationStatus {
	TRIAL,
	ACTIVE,
	SUSPENDED,
	ARCHIVED;
}
