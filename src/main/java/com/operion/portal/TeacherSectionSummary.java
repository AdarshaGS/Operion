package com.operion.portal;

import com.operion.academic.TeacherAssignment;

public record TeacherSectionSummary(
		Long sectionId, String sectionName, String schoolClassDisplayName, String subjectName, String assignmentType) {

	public static TeacherSectionSummary from(TeacherAssignment assignment) {
		return new TeacherSectionSummary(assignment.getSection().getId(), assignment.getSection().getName(),
				assignment.getSection().getSchoolClass().getDisplayName(),
				assignment.getSubject() != null ? assignment.getSubject().getName() : null, assignment.getAssignmentType().name());
	}
}
