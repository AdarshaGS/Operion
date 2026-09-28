package com.operion.student.api;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** {@code admissionNumber} is optional - left null/blank, StudentService auto-generates
 * one from the organisation's configured numbering format (#142). {@code customFields} is
 * keyed by each ACTIVE "STUDENT" CustomFieldDefinition's fieldKey (#146) - null/omitted
 * is treated as "no values". */
public record CreateStudentRequest(@NotNull Long personId, String admissionNumber,
		@NotNull LocalDate admissionDate, String admissionSource, String previousSchool, String tcNumber,
		Double entranceScore, String bloodGroup, String category, String nationality, String remarks,
		String medicalAlerts, String emergencyContactName, String emergencyContactPhone,
		Map<String, Object> customFields) {
}
