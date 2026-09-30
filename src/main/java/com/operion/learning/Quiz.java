package com.operion.learning;

import java.time.Instant;

import com.operion.academic.Section;
import com.operion.academic.Subject;
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

/** A scheduled multiple-choice quiz for one section/subject (#227). Questions lock once published. */
@Getter
@Entity
@Table(name = "quizzes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends TenantScopedEntity {

	/** Where a quiz sits relative to its window - derived from the clock, never stored. */
	public enum Availability {
		UPCOMING, OPEN, CLOSED
	}

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(nullable = false)
	private String title;

	private String instructions;

	@Column(name = "opens_at", nullable = false)
	private Instant opensAt;

	@Column(name = "closes_at", nullable = false)
	private Instant closesAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private QuizStatus status;

	@ManyToOne(optional = false)
	@JoinColumn(name = "created_by_person_id")
	private Person author;

	public Quiz(Section section, Subject subject, String title, String instructions, Instant opensAt, Instant closesAt,
			Person author) {
		this.section = section;
		this.subject = subject;
		this.title = title;
		this.instructions = instructions;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
		this.author = author;
		this.status = QuizStatus.DRAFT;
	}

	public void publish() {
		if (status == QuizStatus.PUBLISHED) {
			throw new IllegalStateException("This quiz is already published");
		}
		this.status = QuizStatus.PUBLISHED;
	}

	public boolean isDraft() {
		return status == QuizStatus.DRAFT;
	}

	public Availability availability(Instant now) {
		if (now.isBefore(opensAt)) {
			return Availability.UPCOMING;
		}
		return now.isBefore(closesAt) ? Availability.OPEN : Availability.CLOSED;
	}
}
