package com.operion.student.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EnrollStudentRequest(@NotNull Long academicYearId, @NotNull Long sectionId, Integer rollNumber,
		@NotNull LocalDate enrolledDate) {
}
