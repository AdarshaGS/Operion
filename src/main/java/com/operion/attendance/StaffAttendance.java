package com.operion.attendance;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.organisation.Campus;
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
 * Deliberately a separate table from StudentAttendance - different FK target (Person,
 * not an enrollment), different fields (check-in/out time), different reporting axis
 * (campus-wide). A polymorphic party_type/party_id merge was considered and rejected as
 * the generic-catch-all anti-pattern the project avoids. Per
 * ai-context/erp-system-plan.md §3.1.
 */
@Getter
@Entity
@Table(name = "staff_attendances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffAttendance extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "attendance_status", nullable = false, length = 20)
	private AttendanceStatus attendanceStatus;

	/** Nullable - e.g. an ABSENT row has no check-in. */
	@Column(name = "check_in_time")
	private Instant checkInTime;

	/** Nullable until the staff member checks out. */
	@Column(name = "check_out_time")
	private Instant checkOutTime;

	/** Nullable. */
	private String remarks;

	public StaffAttendance(Person person, Campus campus, LocalDate attendanceDate, AttendanceStatus attendanceStatus,
			Instant checkInTime, String remarks) {
		this.person = person;
		this.campus = campus;
		this.attendanceDate = attendanceDate;
		this.attendanceStatus = attendanceStatus;
		this.checkInTime = checkInTime;
		this.remarks = remarks;
	}

	public void checkOut(Instant checkOutTime) {
		if (this.checkOutTime != null) {
			throw new IllegalStateException("Staff attendance " + getId() + " is already checked out");
		}
		this.checkOutTime = checkOutTime;
	}

	/** Approved leave overrides whatever attendance was previously recorded for the date. */
	public void markOnLeave() {
		this.attendanceStatus = AttendanceStatus.LEAVE;
	}
}
