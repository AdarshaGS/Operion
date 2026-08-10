package com.operion.communication;

/**
 * ORG/CAMPUS need no audienceRefId (campusId on Announcement already scopes CAMPUS).
 * CLASS/SECTION/INDIVIDUAL resolve audienceRefId against SchoolClass/Section/Student
 * respectively - a polymorphic reference by design, same choice as AuditLog.entityType.
 */
public enum AudienceType {
	ORG,
	CAMPUS,
	CLASS,
	SECTION,
	INDIVIDUAL
}
