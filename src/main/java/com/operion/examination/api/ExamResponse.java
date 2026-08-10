package com.operion.examination.api;

import com.operion.examination.Exam;

public record ExamResponse(Long id, Long academicYearId, String name, String examType, String status) {

	static ExamResponse from(Exam exam) {
		return new ExamResponse(exam.getId(), exam.getAcademicYear().getId(), exam.getName(), exam.getExamType().name(), exam.getStatus().name());
	}
}
