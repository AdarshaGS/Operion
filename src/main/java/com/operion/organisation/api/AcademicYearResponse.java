package com.operion.organisation.api;

import java.time.LocalDate;

import com.operion.organisation.AcademicYear;

public record AcademicYearResponse(Long id, String name, LocalDate startDate, LocalDate endDate, boolean current, String status) {

	static AcademicYearResponse from(AcademicYear academicYear) {
		return new AcademicYearResponse(academicYear.getId(), academicYear.getName(), academicYear.getStartDate(),
				academicYear.getEndDate(), academicYear.isCurrent(), academicYear.getStatus().name());
	}
}
