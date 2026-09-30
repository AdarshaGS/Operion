package com.operion.learning.api;

import java.time.Instant;

import com.operion.learning.Labels;
import com.operion.learning.Quiz;
import com.operion.learning.QuizService;

public record QuizResponse(Long id, Long sectionId, String className, String sectionName, Long subjectId, String subjectName,
		String title, String instructions, Instant opensAt, Instant closesAt, String status, String availability,
		int questionCount, int totalMarks, int attempts, int enrolled) {

	public static QuizResponse from(QuizService.QuizView view, Instant now) {
		Quiz q = view.quiz();
		return new QuizResponse(q.getId(), q.getSection().getId(), Labels.className(q.getSection()), q.getSection().getName(),
				q.getSubject().getId(), q.getSubject().getName(), q.getTitle(), q.getInstructions(), q.getOpensAt(),
				q.getClosesAt(), q.getStatus().name(), q.availability(now).name(), view.questionCount(), view.totalMarks(),
				view.attempts(), view.enrolled());
	}
}
