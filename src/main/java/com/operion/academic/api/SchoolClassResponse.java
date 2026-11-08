package com.operion.academic.api;

import com.operion.academic.SchoolClass;

/** campusName rides along so pickers can tell a grade's per-campus classes apart without a second lookup. */
public record SchoolClassResponse(
		Long id, Long academicYearId, Long campusId, String campusName, Long gradeLevelId, String displayName,
		String status) {

	static SchoolClassResponse from(SchoolClass schoolClass) {
		return new SchoolClassResponse(schoolClass.getId(), schoolClass.getAcademicYear().getId(),
				schoolClass.getCampus().getId(), schoolClass.getCampus().getName(), schoolClass.getGradeLevel().getId(),
				schoolClass.getDisplayName(), schoolClass.getStatus().name());
	}
}
