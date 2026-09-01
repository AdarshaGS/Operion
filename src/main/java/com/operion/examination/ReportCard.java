package com.operion.examination;

import com.operion.common.TenantScopedEntity;
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

/**
 * A published, computed snapshot per (exam, studentEnrollment) - totals/percentage/grade/
 * passed are stored once at publish time (same "snapshot, don't derive live" convention as
 * Invoice.totalAmount), not recomputed if marks are corrected later without re-publishing.
 * Subject-wise marks aren't duplicated here - the API joins back to MarksEntry at read
 * time. classRank is likewise a stored snapshot, recomputed for the whole class cohort by
 * ExaminationService.recomputeClassRanksIfEnabled() whenever a report card in that
 * (exam, class) is (re)published - see #136.
 *
 * status/stale implement the "supersede, don't silently overwrite" correction flow (#138,
 * same convention as StudentFeeAssignment): a correction to marks behind an already-
 * PUBLISHED report card flags it stale (markStale()) rather than mutating totals in place;
 * republishing supersedes the stale row (status -> SUPERSEDED, via supersede()) and inserts
 * a fresh PUBLISHED one, so the old snapshot stays around for audit history.
 */
@Getter
@Entity
@Table(name = "report_cards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCard extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_id")
	private Exam exam;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@Column(name = "total_marks_obtained", nullable = false)
	private Double totalMarksObtained;

	@Column(name = "total_max_marks", nullable = false)
	private Double totalMaxMarks;

	@Column(nullable = false)
	private Double percentage;

	@Column(name = "overall_grade", nullable = false, length = 10)
	private String overallGrade;

	@Column(nullable = false)
	private boolean passed;

	/** Nullable - only set when ranking is enabled for the org (#136). */
	@Column(name = "class_rank")
	private Integer classRank;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportCardStatus status;

	@Column(nullable = false)
	private boolean stale;

	public ReportCard(Exam exam, StudentEnrollment studentEnrollment, Double totalMarksObtained, Double totalMaxMarks,
			Double percentage, String overallGrade, boolean passed) {
		this.exam = exam;
		this.studentEnrollment = studentEnrollment;
		this.totalMarksObtained = totalMarksObtained;
		this.totalMaxMarks = totalMaxMarks;
		this.percentage = percentage;
		this.overallGrade = overallGrade;
		this.passed = passed;
		this.status = ReportCardStatus.PUBLISHED;
		this.stale = false;
	}

	public void assignClassRank(int classRank) {
		this.classRank = classRank;
	}

	/** Only valid while PUBLISHED - flags that a marks correction has invalidated this snapshot. */
	public void markStale() {
		if (status != ReportCardStatus.PUBLISHED) {
			throw new IllegalStateException("Report card " + getId() + " is not PUBLISHED, cannot mark stale");
		}
		this.stale = true;
	}

	public void supersede() {
		if (status != ReportCardStatus.PUBLISHED) {
			throw new IllegalStateException("Report card " + getId() + " is not PUBLISHED, cannot supersede");
		}
		this.status = ReportCardStatus.SUPERSEDED;
	}
}
