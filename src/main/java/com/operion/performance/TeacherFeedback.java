package com.operion.performance;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One reviewer's 1-5 rating of one teacher in one cycle. A reviewer who rates again replaces their earlier rating. */
@Getter
@Entity
@Table(name = "teacher_feedback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherFeedback extends TenantScopedEntity {

	public static final int MIN_RATING = 1;
	public static final int MAX_RATING = 5;

	@ManyToOne(optional = false)
	@JoinColumn(name = "cycle_id")
	private AppraisalCycle cycle;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_person_id")
	private Person teacher;

	@ManyToOne(optional = false)
	@JoinColumn(name = "reviewer_person_id")
	private Person reviewer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeedbackSource source;

	@Column(nullable = false)
	private int rating;

	private String comment;

	public TeacherFeedback(AppraisalCycle cycle, Person teacher, Person reviewer, FeedbackSource source, int rating, String comment) {
		this.cycle = cycle;
		this.teacher = teacher;
		this.reviewer = reviewer;
		revise(source, rating, comment);
	}

	public void revise(FeedbackSource source, int rating, String comment) {
		if (rating < MIN_RATING || rating > MAX_RATING) {
			throw new InvalidRequestException("The rating must be between " + MIN_RATING + " and " + MAX_RATING);
		}
		this.source = source;
		this.rating = rating;
		this.comment = comment == null || comment.isBlank() ? null : comment.trim();
	}
}
