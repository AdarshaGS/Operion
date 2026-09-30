package com.operion.learning;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "quiz_attempt_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttemptAnswer extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "attempt_id")
	private QuizAttempt attempt;

	@ManyToOne(optional = false)
	@JoinColumn(name = "question_id")
	private QuizQuestion question;

	/** Null when the question was left unanswered. */
	@ManyToOne
	@JoinColumn(name = "selected_option_id")
	private QuizOption selectedOption;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "marks_awarded", nullable = false)
	private int marksAwarded;

	public QuizAttemptAnswer(QuizAttempt attempt, QuizQuestion question, QuizOption selectedOption, boolean correct, int marksAwarded) {
		this.attempt = attempt;
		this.question = question;
		this.selectedOption = selectedOption;
		this.correct = correct;
		this.marksAwarded = marksAwarded;
	}
}
