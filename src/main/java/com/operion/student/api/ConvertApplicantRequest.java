package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** {@code admissionNumber} is optional, same as CreateStudentRequest's - convert()
 * delegates to StudentService.admit(), which auto-generates one when null/blank (#142). */
public record ConvertApplicantRequest(String admissionNumber, @NotNull LocalDate admissionDate,
		String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
		String category, String nationality, String remarks, String medicalAlerts, String emergencyContactName,
		String emergencyContactPhone) {
}
