package com.operion.attendance;

import java.time.LocalDate;

import com.operion.academic.SchoolClass;
import com.operion.academic.Section;
import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
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
 * One row per (studentEnrollment, attendanceDate) - tied to the enrollment, not the
 * student, so a re-enrolling student gets a fresh history next year. schoolClass/section
 * are snapshotted at marking time (the register's section), not derived live, so a later
 * mid-year section change never rewrites history. marked_by/marked_at aren't separate
 * columns - BaseEntity's createdBy/createdAt already capture that, since a correction
 * mutates this row in place rather than inserting a new one. Per
 * ai-context/erp-system-plan.md §3.1.
 */
@Getter
@Entity
@Table(name = "student_attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAttendance extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "attendance_status", nullable = false, length = 20)
	private AttendanceStatus attendanceStatus;

	@Column(name = "is_excused", nullable = false)
	private boolean excused;

	/** Nullable. */
	private String remarks;

	public StudentAttendance(StudentEnrollment studentEnrollment, AcademicYear academicYear, SchoolClass schoolClass,
			Section section, LocalDate attendanceDate, AttendanceStatus attendanceStatus, boolean excused, String remarks) {
		this.studentEnrollment = studentEnrollment;
		this.academicYear = academicYear;
		this.schoolClass = schoolClass;
		this.section = section;
		this.attendanceDate = attendanceDate;
		this.attendanceStatus = attendanceStatus;
		this.excused = excused;
		this.remarks = remarks;
	}

	/** Corrections only ever change the status - see AttendanceCorrection for the trail. */
	public void correctStatus(AttendanceStatus newStatus) {
		this.attendanceStatus = newStatus;
	}
}
