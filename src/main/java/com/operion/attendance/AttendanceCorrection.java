package com.operion.attendance;

import com.operion.common.TenantScopedEntity;
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
 * Insert-only trail of edits to StudentAttendance.attendanceStatus after initial
 * marking. corrected_by/corrected_at aren't separate columns - BaseEntity's
 * createdBy/createdAt already capture "who made this correction, when," since this row
 * is only ever inserted, never updated. A mirrored entry also goes to the shared
 * AuditLog (AttendanceService) for cross-entity search - this typed table serves the
 * attendance-specific "correction history" UI. Per ai-context/erp-system-plan.md §3.1.
 */
@Getter
@Entity
@Table(name = "attendance_corrections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrection extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_attendance_id")
	private StudentAttendance studentAttendance;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", nullable = false, length = 20)
	private AttendanceStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false, length = 20)
	private AttendanceStatus newStatus;

	@Column(nullable = false, length = 500)
	private String reason;

	public AttendanceCorrection(StudentAttendance studentAttendance, AttendanceStatus previousStatus,
			AttendanceStatus newStatus, String reason) {
		this.studentAttendance = studentAttendance;
		this.previousStatus = previousStatus;
		this.newStatus = newStatus;
		this.reason = reason;
	}
}
