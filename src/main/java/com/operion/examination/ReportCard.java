package com.operion.examination;

import com.operion.common.TenantScopedEntity;
import com.operion.student.StudentEnrollment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A published, computed snapshot per (exam, studentEnrollment) - totals/percentage/grade
 * are stored once at publish time (same "snapshot, don't derive live" convention as
 * Invoice.totalAmount), not recomputed if marks are corrected later without
 * re-publishing. Subject-wise marks aren't duplicated here - the API joins back to
 * MarksEntry at read time.
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

	public ReportCard(Exam exam, StudentEnrollment studentEnrollment, Double totalMarksObtained, Double totalMaxMarks,
			Double percentage, String overallGrade) {
		this.exam = exam;
		this.studentEnrollment = studentEnrollment;
		this.totalMarksObtained = totalMarksObtained;
		this.totalMaxMarks = totalMaxMarks;
		this.percentage = percentage;
		this.overallGrade = overallGrade;
	}
}
