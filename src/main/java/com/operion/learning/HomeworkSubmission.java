package com.operion.learning;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.student.Student;
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

/** One per (homework, student); a resubmission before review replaces the content in place. */
@Getter
@Entity
@Table(name = "homework_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeworkSubmission extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "homework_id")
	private Homework homework;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "submitted_by_person_id")
	private Person submittedBy;

	@Column(name = "submission_text")
	private String submissionText;

	@Column(name = "attachment_ref")
	private String attachmentRef;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(nullable = false)
	private boolean late;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HomeworkSubmissionStatus status;

	private String feedback;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	public HomeworkSubmission(Homework homework, Student student, Person submittedBy, String submissionText,
			String attachmentRef, Instant submittedAt, boolean late) {
		this.homework = homework;
		this.student = student;
		this.submittedBy = submittedBy;
		this.status = HomeworkSubmissionStatus.SUBMITTED;
		resubmit(submittedBy, submissionText, attachmentRef, submittedAt, late);
	}

	public void resubmit(Person submittedBy, String submissionText, String attachmentRef, Instant submittedAt, boolean late) {
		if (status == HomeworkSubmissionStatus.REVIEWED) {
			throw new IllegalStateException("This submission has already been reviewed and can no longer be changed");
		}
		this.submittedBy = submittedBy;
		this.submissionText = submissionText;
		this.attachmentRef = attachmentRef;
		this.submittedAt = submittedAt;
		this.late = late;
	}

	public void review(String feedback, Instant reviewedAt) {
		this.feedback = feedback;
		this.reviewedAt = reviewedAt;
		this.status = HomeworkSubmissionStatus.REVIEWED;
	}
}
