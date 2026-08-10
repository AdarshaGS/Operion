package com.operion.transport.api;

import java.time.LocalDate;

public record CreateStudentTransportAssignmentRequest(Long studentEnrollmentId, Long routeId, Long routeStopId,
		boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom) {
}
