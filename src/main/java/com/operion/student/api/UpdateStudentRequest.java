package com.operion.student.api;

/** Excludes personId/studentId/admissionNumber/admissionDate - each is either immutable
 * (studentId) or tied to numbering/enrollment logic that a general edit shouldn't touch. */
public record UpdateStudentRequest(String admissionSource, String previousSchool, String tcNumber,
		Double entranceScore, String bloodGroup, String category, String nationality, String remarks,
		String medicalAlerts, String emergencyContactName, String emergencyContactPhone) {
}
