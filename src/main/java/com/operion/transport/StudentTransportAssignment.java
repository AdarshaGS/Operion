package com.operion.transport;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.finance.StudentFeeAssignment;
import com.operion.student.StudentEnrollment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ties a StudentEnrollment (not the bare Student - same history-per-year convention as
 * Attendance/Fees) to a route+stop. One ACTIVE row per enrollment is checked in
 * TransportService and backed by the uq_student_transport_assignments_enrollment_active
 * key over active_marker, same pattern as StudentEnrollment.is_current /
 * StudentGuardian.is_primary_guardian. Route/stop changes end this row and insert a new
 * one (same insert-only pattern as TeacherAssignment reassignment) so the prior
 * route/stop stays queryable as its own historical record. studentFeeAssignment is
 * nullable and optional - set only when the caller supplied a FeeStructure at assignment
 * time (see TransportService.assignStudent); the transport fee then flows through the
 * same invoice/payment/refund pipeline as every other fee, per
 * ai-context/erp-system-plan.md §3.3.
 */
@Getter
@Entity
@Table(name = "student_transport_assignments", uniqueConstraints = {
		@UniqueConstraint(name = "uq_student_transport_assignments_enrollment_active", columnNames = { "student_enrollment_id", "active_marker" }) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentTransportAssignment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_enrollment_id")
	private StudentEnrollment studentEnrollment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "route_id")
	private Route route;

	@ManyToOne(optional = false)
	@JoinColumn(name = "route_stop_id")
	private RouteStop routeStop;

	@Column(name = "uses_pickup", nullable = false)
	private boolean usesPickup;

	@Column(name = "uses_drop", nullable = false)
	private boolean usesDrop;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransportAssignmentStatus status;

	/** TRUE while status is ACTIVE, else NULL, so a unique key can allow at most one active assignment per enrollment. Kept in step by every mutator below. */
	@Column(name = "active_marker")
	private Boolean activeMarker;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	/** Nullable - null while this assignment is active. */
	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	/** Nullable - see class doc. */
	@ManyToOne
	@JoinColumn(name = "student_fee_assignment_id")
	private StudentFeeAssignment studentFeeAssignment;

	public StudentTransportAssignment(StudentEnrollment studentEnrollment, Route route, RouteStop routeStop,
			boolean usesPickup, boolean usesDrop, LocalDate effectiveFrom) {
		if (!usesPickup && !usesDrop) {
			throw new IllegalArgumentException("Assignment must use at least one of pickup or drop");
		}
		this.studentEnrollment = studentEnrollment;
		this.route = route;
		this.routeStop = routeStop;
		this.usesPickup = usesPickup;
		this.usesDrop = usesDrop;
		this.status = TransportAssignmentStatus.ACTIVE;
		this.activeMarker = true;
		this.effectiveFrom = effectiveFrom;
	}

	public void updateLegs(boolean usesPickup, boolean usesDrop) {
		if (status != TransportAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Cannot update legs of an ended assignment");
		}
		if (!usesPickup && !usesDrop) {
			throw new IllegalArgumentException("Assignment must use at least one of pickup or drop");
		}
		this.usesPickup = usesPickup;
		this.usesDrop = usesDrop;
	}

	public void end(LocalDate effectiveTo) {
		if (status != TransportAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Assignment is already ended");
		}
		this.status = TransportAssignmentStatus.ENDED;
		this.activeMarker = null;
		this.effectiveTo = effectiveTo;
	}

	/** Set at most once, at assignment time - see TransportService.assignStudent. */
	public void linkFeeAssignment(StudentFeeAssignment studentFeeAssignment) {
		if (this.studentFeeAssignment != null) {
			throw new IllegalStateException("Transport assignment " + getId() + " is already linked to a fee assignment");
		}
		this.studentFeeAssignment = studentFeeAssignment;
	}
}
