package com.operion.learning;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A student's single, auto-scored attempt at a quiz. */
@Getter
@Entity
@Table(name = "quiz_attempts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "quiz_id")
	private Quiz quiz;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "submitted_by_person_id")
	private Person submittedBy;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(nullable = false)
	private int score;

	@Column(name = "max_score", nullable = false)
	private int maxScore;

	public QuizAttempt(Quiz quiz, Student student, Person submittedBy, Instant submittedAt, int score, int maxScore) {
		this.quiz = quiz;
		this.student = student;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.score = score;
		this.maxScore = maxScore;
	}

	public double percentage() {
		return maxScore == 0 ? 0 : score * 100.0 / maxScore;
	}
}
