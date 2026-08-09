package com.operion.attendance;

import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
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
 * Header row per section+day (DRAFT -> SUBMITTED -> LOCKED), backing a "classes pending
 * attendance today" view without scanning StudentAttendance. No submitted_by/locked_by
 * columns - each transition also writes to the shared AuditLog via AttendanceService,
 * per ai-context/erp-system-plan.md §3.1.
 */
@Getter
@Entity
@Table(name = "class_attendance_registers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassAttendanceRegister extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "register_status", nullable = false, length = 20)
	private ClassAttendanceRegisterStatus registerStatus;

	public ClassAttendanceRegister(AcademicYear academicYear, Section section, LocalDate attendanceDate) {
		this.academicYear = academicYear;
		this.section = section;
		this.attendanceDate = attendanceDate;
		this.registerStatus = ClassAttendanceRegisterStatus.DRAFT;
	}

	public void submit() {
		if (registerStatus != ClassAttendanceRegisterStatus.DRAFT) {
			throw new IllegalStateException("Register " + getId() + " is not in DRAFT, cannot submit");
		}
		this.registerStatus = ClassAttendanceRegisterStatus.SUBMITTED;
	}

	public void lock() {
		if (registerStatus != ClassAttendanceRegisterStatus.SUBMITTED) {
			throw new IllegalStateException("Register " + getId() + " is not SUBMITTED, cannot lock");
		}
		this.registerStatus = ClassAttendanceRegisterStatus.LOCKED;
	}
}
