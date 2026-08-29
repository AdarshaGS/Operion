package com.operion.transport.api;

import java.time.LocalDate;

/** feeStructureId is nullable - see TransportService.assignStudent for what supplying it does. */
public record CreateStudentTransportAssignmentRequest(Long studentEnrollmentId, Long routeId, Long routeStopId,
		boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom, Long feeStructureId) {
}
