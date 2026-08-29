package com.operion.transport.api;

import com.operion.transport.StudentTransportAssignment;

public record RouteRosterEntryResponse(Long assignmentId, Long studentEnrollmentId, String studentName, String admissionNumber,
		Long routeStopId, String stopName, int sequenceNumber, boolean usesPickup, boolean usesDrop) {

	public static RouteRosterEntryResponse from(StudentTransportAssignment assignment) {
		var student = assignment.getStudentEnrollment().getStudent();
		var person = student.getPerson();
		var routeStop = assignment.getRouteStop();
		return new RouteRosterEntryResponse(assignment.getId(), assignment.getStudentEnrollment().getId(),
				person.getFirstName() + " " + person.getLastName(), student.getAdmissionNumber(), routeStop.getId(),
				routeStop.getStopName(), routeStop.getSequenceNumber(), assignment.isUsesPickup(), assignment.isUsesDrop());
	}
}
