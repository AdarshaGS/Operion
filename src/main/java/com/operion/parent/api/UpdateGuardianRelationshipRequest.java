package com.operion.parent.api;

/** Partial update: a field left out (null) keeps the link's stored value. */
public record UpdateGuardianRelationshipRequest(String relationshipType, Boolean primaryGuardian,
		Boolean emergencyContact, Boolean canPickup, Boolean canReceiveCommunication, Integer contactPriority) {
}
