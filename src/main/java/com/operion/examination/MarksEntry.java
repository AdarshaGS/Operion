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
 * One row per (examSchedule, studentEnrollment), created only when marks are actually
 * entered - no upfront placeholder rows, same convention as StudentAttendance.
 * Corrections mutate this row in place and rely on the shared AuditLog for the trail
 * (ExaminationService.correctMarks) rather than a dedicated correction table - unlike
 * Attendance, nothing here signals the extra typed-table need, so the simpler
 * mutate+AuditLog pattern (already used by StudentEnrollment.reassignSection) applies.
 */
@Getter
@Entity
@Table(name = "marks_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarksEntry extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_schedule_id")
	private ExamSchedule examSchedule;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@Column(name = "marks_obtained", nullable = false)
	private Double marksObtained;

	@Column(name = "is_absent", nullable = false)
	private boolean absent;

	/** Nullable. */
	private String remarks;

	public MarksEntry(ExamSchedule examSchedule, StudentEnrollment studentEnrollment, Double marksObtained, boolean absent, String remarks) {
		this.examSchedule = examSchedule;
		this.studentEnrollment = studentEnrollment;
		this.marksObtained = absent ? 0.0 : marksObtained;
		this.absent = absent;
		this.remarks = remarks;
	}

	public void correctMarks(Double marksObtained, boolean absent, String remarks) {
		this.marksObtained = absent ? 0.0 : marksObtained;
		this.absent = absent;
		this.remarks = remarks;
	}
}
