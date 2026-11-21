package com.operion.hr.api;

/**
 * Whether a staff member can sign in. {@code hasAccess} means an ACTIVE membership exists for their person.
 * {@code status} is the member status of the most relevant membership (INVITED / ACTIVE / INACTIVE, see MemberStatus),
 * null when the person has never been given access. {@code membershipId} and {@code roleName} reveal who holds which
 * role, so they are filled only for callers who may view memberships ({@link #withoutDetail()} otherwise).
 */
public record StaffAccessSummary(boolean hasAccess, Long membershipId, String roleName, String status) {

	public static final StaffAccessSummary NONE = new StaffAccessSummary(false, null, null, null);

	public StaffAccessSummary withoutDetail() {
		return new StaffAccessSummary(hasAccess, null, null, status);
	}
}
