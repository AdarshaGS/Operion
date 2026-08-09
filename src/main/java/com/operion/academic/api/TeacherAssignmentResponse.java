package com.operion.academic.api;

import java.time.LocalDate;

import com.operion.academic.TeacherAssignment;

public record TeacherAssignmentResponse(Long id, Long sectionId, Long subjectId, Long teacherPersonId,
		String assignmentType, LocalDate startDate, LocalDate endDate, String status) {

	static TeacherAssignmentResponse from(TeacherAssignment assignment) {
		return new TeacherAssignmentResponse(assignment.getId(), assignment.getSection().getId(),
				assignment.getSubject() != null ? assignment.getSubject().getId() : null,
				assignment.getTeacherPerson().getId(), assignment.getAssignmentType().name(), assignment.getStartDate(),
				assignment.getEndDate(), assignment.getStatus().name());
	}
}
