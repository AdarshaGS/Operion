package com.operion.finance;

import java.math.BigDecimal;

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
 * Links a StudentEnrollment (not the bare Student - ties the obligation to the specific
 * year, same reasoning as StudentAttendance) to a FeeStructure, carrying any discount.
 * baseAmount is snapshotted from FeeStructure.amount at assignment time - a later
 * class-wide rate edit must never silently change what an already-committed student
 * owes. effectiveAmount is stored, not computed, forcing any revision through an
 * explicit action (updateDiscount/supersede). Mutable pre-invoice; once any Invoice
 * exists off this row, FeeService supersedes it (new row, this row -> SUPERSEDED)
 * instead of mutating it, to protect invoice provenance. Per
 * ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "student_fee_assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentFeeAssignment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_structure_id")
	private FeeStructure feeStructure;

	@Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal baseAmount;

	@Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal discountAmount;

	@Column(name = "effective_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal effectiveAmount;

	/** Nullable - only set when discountAmount > 0. */
	@Column(name = "discount_reason")
	private String discountReason;

	/** Nullable - only set when discountAmount > 0; gated by FEE_DISCOUNT_APPROVE, not FEE_ASSIGNMENT_MANAGE. */
	@Column(name = "approved_by")
	private Long approvedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudentFeeAssignmentStatus status;

	public StudentFeeAssignment(StudentEnrollment studentEnrollment, FeeStructure feeStructure, BigDecimal baseAmount,
			BigDecimal discountAmount, BigDecimal effectiveAmount, String discountReason, Long approvedBy) {
		this.studentEnrollment = studentEnrollment;
		this.feeStructure = feeStructure;
		this.baseAmount = baseAmount;
		this.discountAmount = discountAmount;
		this.effectiveAmount = effectiveAmount;
		this.discountReason = discountReason;
		this.approvedBy = approvedBy;
		this.status = StudentFeeAssignmentStatus.ACTIVE;
	}

	/** Pre-invoice only - FeeService.reviseAssignment() supersedes instead once an Invoice exists. */
	public void updateDiscount(BigDecimal discountAmount, BigDecimal effectiveAmount, String discountReason, Long approvedBy) {
		if (status != StudentFeeAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Fee assignment " + getId() + " is not ACTIVE, cannot update");
		}
		this.discountAmount = discountAmount;
		this.effectiveAmount = effectiveAmount;
		this.discountReason = discountReason;
		this.approvedBy = approvedBy;
	}

	public void supersede() {
		if (status != StudentFeeAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Fee assignment " + getId() + " is not ACTIVE, cannot supersede");
		}
		this.status = StudentFeeAssignmentStatus.SUPERSEDED;
	}
}
