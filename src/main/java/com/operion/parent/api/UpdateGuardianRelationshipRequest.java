package com.operion.parent.api;

public record UpdateGuardianRelationshipRequest(String relationshipType, boolean primaryGuardian,
		boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication, int contactPriority) {
}
