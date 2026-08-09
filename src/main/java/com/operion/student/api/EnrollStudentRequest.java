package com.operion.student.api;

import java.time.LocalDate;

public record EnrollStudentRequest(Long academicYearId, Long sectionId, Integer rollNumber, LocalDate enrolledDate) {
}
