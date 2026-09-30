package com.operion.learning;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.examination.ExamSchedule;
import com.operion.identity.Person;
import com.operion.student.StudentEnrollment;
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

/** A student's scanned answer sheet for one exam schedule (#230). UPLOADED -> MARKED -> RELEASED. */
@Getter
@Entity
@Table(name = "answer_sheets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerSheet extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_schedule_id")
	private ExamSchedule examSchedule;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnswerSheetStatus status;

	@Column(name = "total_marks")
	private Double totalMarks;

	@Column(name = "overall_remarks")
	private String overallRemarks;

	@ManyToOne(optional = false)
	@JoinColumn(name = "uploaded_by_person_id")
	private Person uploadedBy;

	@ManyToOne
	@JoinColumn(name = "marked_by_person_id")
	private Person markedBy;

	@Column(name = "marked_at")
	private Instant markedAt;

	@Column(name = "released_at")
	private Instant releasedAt;

	public AnswerSheet(ExamSchedule examSchedule, StudentEnrollment studentEnrollment, Person uploadedBy) {
		this.examSchedule = examSchedule;
		this.studentEnrollment = studentEnrollment;
		this.uploadedBy = uploadedBy;
		this.status = AnswerSheetStatus.UPLOADED;
	}

	public void mark(Double totalMarks, String overallRemarks, Person markedBy, Instant now) {
		if (status == AnswerSheetStatus.RELEASED) {
			throw new IllegalStateException("This sheet has been released and can no longer be changed");
		}
		this.totalMarks = totalMarks;
		this.overallRemarks = overallRemarks;
		this.markedBy = markedBy;
		this.markedAt = now;
		this.status = AnswerSheetStatus.MARKED;
	}

	public void release(Instant now) {
		if (status != AnswerSheetStatus.MARKED) {
			throw new IllegalStateException("Only a marked sheet can be released");
		}
		this.status = AnswerSheetStatus.RELEASED;
		this.releasedAt = now;
	}
}
