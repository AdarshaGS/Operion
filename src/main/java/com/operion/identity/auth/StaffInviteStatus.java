package com.operion.identity.auth;

/** Mirrors com.operion.parent.PortalInviteStatus - duplicated rather than shared so this
 * module and the parent-portal module stay independently comprehensible, same reasoning
 * PortalInviteService gives for duplicating GUARDIAN_ROLE_NAME rather than importing it. */
public enum StaffInviteStatus {
	PENDING,
	CLAIMED
}
