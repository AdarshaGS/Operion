package com.operion.student.api;

import java.time.LocalDate;

public record CreateStudentRequest(Long personId, String admissionNumber, LocalDate admissionDate,
		String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
		String category, String nationality, String remarks) {
}
