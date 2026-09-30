package com.operion.learning.api;

import java.time.Instant;

import com.operion.learning.Labels;
import com.operion.learning.QuizService;

public record QuizResultRowResponse(Long studentId, String studentName, Integer rollNumber, boolean attempted, Integer score,
		Integer maxScore, Double percentage, Instant submittedAt) {

	public static QuizResultRowResponse from(QuizService.ResultRow row) {
		var student = row.enrollment().getStudent();
		var a = row.attempt();
		return new QuizResultRowResponse(student.getId(), Labels.personName(student.getPerson()), row.enrollment().getRollNumber(),
				a != null, a == null ? null : a.getScore(), a == null ? null : a.getMaxScore(), a == null ? null : a.percentage(),
				a == null ? null : a.getSubmittedAt());
	}
}
