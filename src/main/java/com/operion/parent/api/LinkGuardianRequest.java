package com.operion.parent.api;

public record LinkGuardianRequest(Long guardianId, String relationshipType, boolean primaryGuardian,
		boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication, int contactPriority) {
}
