package com.operion.hr;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per staff+leaveType+AcademicYear holding the entitlement - explicit, no
 * wildcard, same "require an explicit row" philosophy as FeeStructure. "Used" days are
 * not stored here; HrService computes them live by summing APPROVED LeaveRequests,
 * mirroring Inventory's live-balance pattern.
 */
@Getter
@Entity
@Table(name = "leave_balances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalance extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@ManyToOne(optional = false)
	@JoinColumn(name = "leave_type_id")
	private LeaveType leaveType;

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@Column(name = "allocated_days", nullable = false)
	private double allocatedDays;

	public LeaveBalance(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear, double allocatedDays) {
		this.staffProfile = staffProfile;
		this.leaveType = leaveType;
		this.academicYear = academicYear;
		this.allocatedDays = allocatedDays;
	}

	public void updateAllocation(double allocatedDays) {
		this.allocatedDays = allocatedDays;
	}
}
