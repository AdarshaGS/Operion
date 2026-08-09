package com.operion.student.api;

import java.time.LocalDate;

public record PromoteStudentRequest(
		Long academicYearId, Long sectionId, Integer rollNumber, LocalDate promotionDate, boolean repeated) {
}
