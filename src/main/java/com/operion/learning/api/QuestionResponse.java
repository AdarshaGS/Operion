package com.operion.learning.api;

import java.util.List;

import com.operion.learning.QuizOption;
import com.operion.learning.QuizService;

/** Staff view of a question. correct is only ever populated here - never on the guardian-facing DTO. */
public record QuestionResponse(Long id, Long subjectId, int ordinal, String text, int marks, List<OptionResponse> options) {

	public record OptionResponse(Long id, String text, boolean correct) {
		static OptionResponse from(QuizOption o) {
			return new OptionResponse(o.getId(), o.getText(), o.isCorrect());
		}
	}

	public static QuestionResponse from(QuizService.QuestionView v) {
		var q = v.question();
		return new QuestionResponse(q.getId(), q.getSubject().getId(), q.getOrdinal(), q.getText(), q.getMarks(),
				v.options().stream().map(OptionResponse::from).toList());
	}
}
