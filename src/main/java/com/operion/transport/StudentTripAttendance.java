package com.operion.transport;

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
 * One row per (tripLog, studentEnrollment) - the per-student boarding record TripLog's
 * own class doc deferred to v2 ("StudentTripAttendance seam"), same split as
 * ClassAttendanceRegister/StudentAttendance. Tied to the enrollment, not the bare
 * Student, same convention as StudentAttendance. TransportService enforces one row per
 * (trip, enrollment) - use a fresh mark to correct rather than a second insert, same
 * precedent as StudentAttendance/AttendanceService.
 */
@Getter
@Entity
@Table(name = "student_trip_attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentTripAttendance extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "trip_log_id")
	private TripLog tripLog;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@Enumerated(EnumType.STRING)
	@Column(name = "boarding_status", nullable = false, length = 20)
	private BoardingStatus boardingStatus;

	/** Nullable. */
	private String remarks;

	public StudentTripAttendance(TripLog tripLog, StudentEnrollment studentEnrollment, BoardingStatus boardingStatus, String remarks) {
		this.tripLog = tripLog;
		this.studentEnrollment = studentEnrollment;
		this.boardingStatus = boardingStatus;
		this.remarks = remarks;
	}

	/** Corrections only ever change the status - same precedent as StudentAttendance.correctStatus. */
	public void correctStatus(BoardingStatus newStatus) {
		this.boardingStatus = newStatus;
	}
}
