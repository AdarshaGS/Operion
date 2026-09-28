package com.operion.student.api;

import java.util.Map;

/** Excludes personId/studentId/admissionNumber/admissionDate - each is either immutable
 * (studentId) or tied to numbering/enrollment logic that a general edit shouldn't touch.
 * {@code customFields} replaces the full set of stored values (#146) - omit a previously-
 * set key to clear it, same "send the complete state" convention as the other fields here. */
public record UpdateStudentRequest(String admissionSource, String previousSchool, String tcNumber,
		Double entranceScore, String bloodGroup, String category, String nationality, String remarks,
		String medicalAlerts, String emergencyContactName, String emergencyContactPhone,
		Map<String, Object> customFields) {
}
