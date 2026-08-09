package com.operion.academic.api;

import com.operion.academic.SchoolClass;

public record SchoolClassResponse(
		Long id, Long academicYearId, Long campusId, Long gradeLevelId, String displayName, String status) {

	static SchoolClassResponse from(SchoolClass schoolClass) {
		return new SchoolClassResponse(schoolClass.getId(), schoolClass.getAcademicYear().getId(),
				schoolClass.getCampus().getId(), schoolClass.getGradeLevel().getId(), schoolClass.getDisplayName(),
				schoolClass.getStatus().name());
	}
}
