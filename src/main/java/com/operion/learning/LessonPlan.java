package com.operion.learning;

import java.time.Instant;
import java.time.LocalDate;

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

/**
 * A teacher-authored plan for one section/subject on one date, reviewed by an admin before it is
 * "official" (#229). Same lock-then-explicit-change shape as published report cards: once
 * APPROVED it can no longer be edited.
 */
@Getter
@Entity
@Table(name = "lesson_plans")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonPlan extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(name = "plan_date", nullable = false)
	private LocalDate planDate;

	@Column(nullable = false)
	private String title;

	private String objectives;

	private String activities;

	private String resources;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LessonPlanStatus status;

	@ManyToOne(optional = false)
	@JoinColumn(name = "submitted_by_person_id")
	private Person author;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "review_note")
	private String reviewNote;

	@ManyToOne
	@JoinColumn(name = "reviewed_by_person_id")
	private Person reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	public LessonPlan(Section section, Subject subject, LocalDate planDate, String title, String objectives,
			String activities, String resources, Person author) {
		this.section = section;
		this.subject = subject;
		this.planDate = planDate;
		this.title = title;
		this.objectives = objectives;
		this.activities = activities;
		this.resources = resources;
		this.author = author;
		this.status = LessonPlanStatus.DRAFT;
	}

	public void edit(LocalDate planDate, String title, String objectives, String activities, String resources) {
		if (status != LessonPlanStatus.DRAFT && status != LessonPlanStatus.REJECTED) {
			throw new IllegalStateException("Only a draft or rejected lesson plan can be edited");
		}
		this.planDate = planDate;
		this.title = title;
		this.objectives = objectives;
		this.activities = activities;
		this.resources = resources;
	}

	public void submit(Instant now) {
		if (status != LessonPlanStatus.DRAFT && status != LessonPlanStatus.REJECTED) {
			throw new IllegalStateException("Only a draft or rejected lesson plan can be submitted");
		}
		this.status = LessonPlanStatus.SUBMITTED;
		this.submittedAt = now;
		this.reviewNote = null;
		this.reviewedBy = null;
		this.reviewedAt = null;
	}

	public void approve(Person reviewer, String note, Instant now) {
		requireSubmitted();
		this.status = LessonPlanStatus.APPROVED;
		recordReview(reviewer, note, now);
	}

	public void reject(Person reviewer, String note, Instant now) {
		requireSubmitted();
		this.status = LessonPlanStatus.REJECTED;
		recordReview(reviewer, note, now);
	}

	private void requireSubmitted() {
		if (status != LessonPlanStatus.SUBMITTED) {
			throw new IllegalStateException("Only a submitted lesson plan can be reviewed");
		}
	}

	private void recordReview(Person reviewer, String note, Instant now) {
		this.reviewedBy = reviewer;
		this.reviewNote = note;
		this.reviewedAt = now;
	}
}
