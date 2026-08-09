package com.operion.academic.api;

import com.operion.academic.GradeLevel;

public record GradeLevelResponse(Long id, String name, int sequenceOrder, String stage, String status) {

	static GradeLevelResponse from(GradeLevel gradeLevel) {
		return new GradeLevelResponse(gradeLevel.getId(), gradeLevel.getName(), gradeLevel.getSequenceOrder(),
				gradeLevel.getStage(), gradeLevel.getStatus().name());
	}
}
