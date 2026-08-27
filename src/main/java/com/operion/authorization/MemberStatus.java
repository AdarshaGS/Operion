package com.operion.authorization;

import com.operion.identity.UserStatus;

/**
 * The single "member status" the product spec describes (invited/active/inactive),
 * computed from the three independent statuses that actually exist today
 * (User.status, OrganisationMembership.status, and - for staff - StaffProfile.status)
 * rather than a new stored column (GitHub #106). Only the first two are considered here;
 * a staff member's own HR lifecycle (resigned/terminated) is a separate concern surfaced
 * by the HR module itself, not folded into this generic membership-level status.
 */
public enum MemberStatus {
	/** Invite issued but never claimed - the User row exists as a login shell only. */
	INVITED,
	ACTIVE,
	/** Either this membership was revoked, or the underlying User is LOCKED/DISABLED. */
	INACTIVE;

	public static MemberStatus of(UserStatus userStatus, MembershipStatus membershipStatus) {
		if (userStatus == UserStatus.PENDING) {
			return INVITED;
		}
		if (membershipStatus == MembershipStatus.INACTIVE || userStatus == UserStatus.LOCKED || userStatus == UserStatus.DISABLED) {
			return INACTIVE;
		}
		return ACTIVE;
	}
}
