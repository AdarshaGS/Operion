package com.operion.student.api;

import java.time.LocalDate;
import java.util.Map;

import com.operion.customfield.CustomFieldService;
import com.operion.student.Student;

public record StudentResponse(Long id, Long personId, String studentId, String admissionNumber, LocalDate admissionDate,
		String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
		String category, String nationality, String remarks, String medicalAlerts, String emergencyContactName,
		String emergencyContactPhone, String status, Map<String, Object> customFields) {

	static StudentResponse from(Student student, CustomFieldService customFieldService) {
		return new StudentResponse(student.getId(), student.getPerson().getId(), student.getStudentId(),
				student.getAdmissionNumber(), student.getAdmissionDate(), student.getAdmissionSource(),
				student.getPreviousSchool(), student.getTcNumber(), student.getEntranceScore(), student.getBloodGroup(),
				student.getCategory(), student.getNationality(), student.getRemarks(), student.getMedicalAlerts(),
				student.getEmergencyContactName(), student.getEmergencyContactPhone(), student.getStatus().name(),
				customFieldService.deserialize(student.getCustomFields()));
	}

	/** For list/search/export endpoints, where the values themselves aren't rendered and
	 * fetching+deserializing them for every row would be pure waste. */
	static StudentResponse from(Student student) {
		return new StudentResponse(student.getId(), student.getPerson().getId(), student.getStudentId(),
				student.getAdmissionNumber(), student.getAdmissionDate(), student.getAdmissionSource(),
				student.getPreviousSchool(), student.getTcNumber(), student.getEntranceScore(), student.getBloodGroup(),
				student.getCategory(), student.getNationality(), student.getRemarks(), student.getMedicalAlerts(),
				student.getEmergencyContactName(), student.getEmergencyContactPhone(), student.getStatus().name(),
				Map.of());
	}
}
