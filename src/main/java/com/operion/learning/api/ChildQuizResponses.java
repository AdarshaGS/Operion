package com.operion.learning.api;

import java.time.Instant;
import java.util.List;

import com.operion.learning.QuizAttempt;
import com.operion.learning.QuizOption;
import com.operion.learning.QuizService;

/** Guardian-facing quiz DTOs. None of them expose which option is correct while an attempt is possible. */
public final class ChildQuizResponses {

	private ChildQuizResponses() {
	}

	public record ChildQuiz(Long id, String subjectName, String title, String instructions, Instant opensAt, Instant closesAt,
			String availability, int questionCount, int totalMarks, boolean attempted, Integer score, Integer maxScore) {

		public static ChildQuiz from(QuizService.ChildQuizView v) {
			var q = v.quiz();
			QuizAttempt a = v.attempt();
			return new ChildQuiz(q.getId(), q.getSubject().getName(), q.getTitle(), q.getInstructions(), q.getOpensAt(),
					q.getClosesAt(), v.availability().name(), v.questionCount(), v.totalMarks(), a != null,
					a == null ? null : a.getScore(), a == null ? null : a.getMaxScore());
		}
	}

	public record AttemptOption(Long id, String text) {
	}

	public record AttemptQuestion(Long id, int ordinal, String text, int marks, List<AttemptOption> options) {

		public static AttemptQuestion from(QuizService.QuestionView v) {
			var q = v.question();
			return new AttemptQuestion(q.getId(), q.getOrdinal(), q.getText(), q.getMarks(),
					v.options().stream().map(o -> new AttemptOption(o.getId(), o.getText())).toList());
		}
	}

	public record AnswerInput(Long questionId, Long optionId) {
	}

	public record AttemptRequest(List<AnswerInput> answers) {
	}

	public record AttemptSummary(Long quizId, String quizTitle, String subjectName, int score, int maxScore, double percentage,
			Instant submittedAt) {

		public static AttemptSummary from(QuizAttempt a) {
			return new AttemptSummary(a.getQuiz().getId(), a.getQuiz().getTitle(), a.getQuiz().getSubject().getName(),
					a.getScore(), a.getMaxScore(), a.percentage(), a.getSubmittedAt());
		}
	}

	/** correctOptionId is null until the quiz has closed. */
	public record AnswerResult(Long questionId, String text, int marks, List<AttemptOption> options, Long selectedOptionId,
			Long correctOptionId, boolean correct, int marksAwarded) {
	}

	public record AttemptResult(AttemptSummary summary, boolean correctAnswersShown, List<AnswerResult> answers) {

		public static AttemptResult from(QuizService.AttemptDetail d) {
			var answers = d.answers().stream().map(a -> new AnswerResult(a.question().getId(), a.question().getText(),
					a.question().getMarks(), a.options().stream().map(o -> new AttemptOption(o.getId(), o.getText())).toList(),
					a.selectedOptionId(),
					d.correctAnswersShown() ? a.options().stream().filter(QuizOption::isCorrect).map(QuizOption::getId).findFirst().orElse(null) : null,
					a.correct(), a.marksAwarded())).toList();
			return new AttemptResult(AttemptSummary.from(d.attempt()), d.correctAnswersShown(), answers);
		}
	}
}
