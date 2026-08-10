package com.operion.hr;

import java.time.Instant;
import java.time.LocalDate;

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
 * PENDING -> APPROVED/REJECTED/CANCELLED, one-way off PENDING, same convention as
 * Announcement publish/cancel. numberOfDays is Double, not BigDecimal - non-money
 * decimal quantity (supports half-days), same precedent as exam marks. Approving
 * against insufficient balance is blocked in HrService, not here - it needs to sum
 * sibling requests, which is a repository-level concern.
 */
@Getter
@Entity
@Table(name = "leave_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@ManyToOne(optional = false)
	@JoinColumn(name = "leave_type_id")
	private LeaveType leaveType;

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "number_of_days", nullable = false)
	private double numberOfDays;

	/** Nullable. */
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LeaveRequestStatus status;

	/** Nullable - unset until approved/rejected. References a User id, no FK by design. */
	@Column(name = "approved_by")
	private Long approvedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	public LeaveRequest(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear, LocalDate startDate,
			LocalDate endDate, double numberOfDays, String reason) {
		if (numberOfDays <= 0) {
			throw new IllegalArgumentException("Leave request numberOfDays must be positive");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Leave request endDate cannot be before startDate");
		}
		this.staffProfile = staffProfile;
		this.leaveType = leaveType;
		this.academicYear = academicYear;
		this.startDate = startDate;
		this.endDate = endDate;
		this.numberOfDays = numberOfDays;
		this.reason = reason;
		this.status = LeaveRequestStatus.PENDING;
	}

	public void approve(Long approvedBy) {
		requirePending();
		this.status = LeaveRequestStatus.APPROVED;
		this.approvedBy = approvedBy;
		this.decidedAt = Instant.now();
	}

	public void reject(Long decidedBy) {
		requirePending();
		this.status = LeaveRequestStatus.REJECTED;
		this.approvedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	public void cancel() {
		requirePending();
		this.status = LeaveRequestStatus.CANCELLED;
	}

	private void requirePending() {
		if (status != LeaveRequestStatus.PENDING) {
			throw new IllegalStateException("Only a pending leave request can be decided, was " + status);
		}
	}
}
