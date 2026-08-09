package com.operion.parent.api;

import com.operion.parent.StudentGuardian;

public record StudentGuardianResponse(Long id, Long studentId, Long guardianId, String relationshipType,
		boolean primaryGuardian, boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication,
		int contactPriority, String status) {

	static StudentGuardianResponse from(StudentGuardian studentGuardian) {
		return new StudentGuardianResponse(studentGuardian.getId(), studentGuardian.getStudent().getId(),
				studentGuardian.getGuardian().getId(), studentGuardian.getRelationshipType().name(),
				studentGuardian.isPrimaryGuardian(), studentGuardian.isEmergencyContact(), studentGuardian.isCanPickup(),
				studentGuardian.isCanReceiveCommunication(), studentGuardian.getContactPriority(),
				studentGuardian.getStatus().name());
	}
}
