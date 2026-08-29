package com.operion.communication;

/**
 * ORG/CAMPUS/STAFF need no audienceRefId (campusId on Announcement already scopes
 * CAMPUS; STAFF resolves org-wide same as ORG, just filtered to active StaffProfile
 * holders). CLASS/SECTION/INDIVIDUAL/STAFF_MEMBER resolve audienceRefId against
 * SchoolClass/Section/Student/StaffProfile respectively - a polymorphic reference by
 * design, same choice as AuditLog.entityType. SELECTED_GROUP is the one exception: an
 * ad-hoc, admin-chosen set of Persons that doesn't fit a single audienceRefId, so it's
 * stored as AnnouncementAudienceMember rows instead.
 */
public enum AudienceType {
	ORG,
	CAMPUS,
	CLASS,
	SECTION,
	INDIVIDUAL,
	STAFF,
	STAFF_MEMBER,
	SELECTED_GROUP
}
