package com.operion.academic.api;

public record CreateSchoolClassRequest(Long academicYearId, Long campusId, Long gradeLevelId, String displayName) {
}
