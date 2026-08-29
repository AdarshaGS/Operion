package com.operion.transport.api;

import java.time.LocalDate;

import com.operion.transport.StudentTransportAssignment;

public record StudentTransportAssignmentResponse(Long id, Long studentEnrollmentId, Long routeId, Long routeStopId,
		boolean usesPickup, boolean usesDrop, String status, LocalDate effectiveFrom, LocalDate effectiveTo,
		Long studentFeeAssignmentId) {

	public static StudentTransportAssignmentResponse from(StudentTransportAssignment assignment) {
		return new StudentTransportAssignmentResponse(assignment.getId(), assignment.getStudentEnrollment().getId(),
				assignment.getRoute().getId(), assignment.getRouteStop().getId(), assignment.isUsesPickup(),
				assignment.isUsesDrop(), assignment.getStatus().name(), assignment.getEffectiveFrom(), assignment.getEffectiveTo(),
				assignment.getStudentFeeAssignment() == null ? null : assignment.getStudentFeeAssignment().getId());
	}
}
