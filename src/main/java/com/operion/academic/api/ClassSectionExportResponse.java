package com.operion.academic.api;

import com.operion.academic.SchoolClass;
import com.operion.academic.Section;

public record ClassSectionExportResponse(Long sectionId, String academicYear, String campus, String gradeLevel,
		String className, String sectionName, Integer capacity, String room) {

	static ClassSectionExportResponse from(Section section) {
		SchoolClass schoolClass = section.getSchoolClass();
		return new ClassSectionExportResponse(section.getId(), schoolClass.getAcademicYear().getName(),
				schoolClass.getCampus().getName(), schoolClass.getGradeLevel().getName(), schoolClass.getDisplayName(),
				section.getName(), section.getCapacity(), section.getRoom());
	}
}
