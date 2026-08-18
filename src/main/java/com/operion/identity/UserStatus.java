package com.operion.identity;

public enum UserStatus {
	/** Login shell created via a staff invite (see StaffInviteService) with no real password
	 * set yet - blocked from logging in the same as LOCKED/DISABLED until the invite is claimed. */
	PENDING,
	ACTIVE,
	LOCKED,
	DISABLED
}
