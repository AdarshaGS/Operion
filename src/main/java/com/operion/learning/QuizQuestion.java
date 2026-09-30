package com.operion.learning;

import com.operion.academic.Subject;
import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** quiz == null means a reusable question-bank item for the subject; otherwise a question of that quiz. */
@Getter
@Entity
@Table(name = "quiz_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion extends TenantScopedEntity {

	@ManyToOne
	@JoinColumn(name = "quiz_id")
	private Quiz quiz;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(nullable = false)
	private int ordinal;

	@Column(name = "question_text", nullable = false)
	private String text;

	@Column(nullable = false)
	private int marks;

	@ManyToOne(optional = false)
	@JoinColumn(name = "created_by_person_id")
	private Person author;

	public QuizQuestion(Quiz quiz, Subject subject, int ordinal, String text, int marks, Person author) {
		this.quiz = quiz;
		this.subject = subject;
		this.ordinal = ordinal;
		this.text = text;
		this.marks = marks;
		this.author = author;
	}
}
