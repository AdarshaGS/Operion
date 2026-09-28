package com.operion.transport.api;

import com.operion.transport.StudentTripAttendance;

public record StudentTripAttendanceResponse(Long id, Long tripLogId, Long studentEnrollmentId, String studentName,
		String admissionNumber, String boardingStatus, String remarks) {

	public static StudentTripAttendanceResponse from(StudentTripAttendance attendance) {
		var student = attendance.getStudentEnrollment().getStudent();
		var person = student.getPerson();
		return new StudentTripAttendanceResponse(attendance.getId(), attendance.getTripLog().getId(),
				attendance.getStudentEnrollment().getId(), person.getFirstName() + " " + person.getLastName(),
				student.getAdmissionNumber(), attendance.getBoardingStatus().name(), attendance.getRemarks());
	}
}
