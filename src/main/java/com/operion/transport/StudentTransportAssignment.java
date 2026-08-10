package com.operion.transport;

import java.time.LocalDate;

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
 * Ties a StudentEnrollment (not the bare Student - same history-per-year convention as
 * Attendance/Fees) to a route+stop. One ACTIVE row per enrollment is enforced in
 * TransportService, same pattern as StudentEnrollment.is_current /
 * StudentGuardian.is_primary_guardian. Route/stop changes mutate this row in place
 * (mirrors StudentEnrollment.reassignSection) rather than superseding - nothing here
 * triggers invoicing, unlike StudentFeeAssignment.
 */
@Getter
@Entity
@Table(name = "student_transport_assignments")
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

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	/** Nullable - null while this assignment is active. */
	@Column(name = "effective_to")
	private LocalDate effectiveTo;

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
		this.effectiveFrom = effectiveFrom;
	}

	/** Mid-year route/stop change on an active assignment - see class doc. */
	public void reassignRoute(Route route, RouteStop routeStop) {
		if (status != TransportAssignmentStatus.ACTIVE) {
			throw new IllegalStateException("Cannot reassign the route of an ended assignment");
		}
		this.route = route;
		this.routeStop = routeStop;
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
		this.effectiveTo = effectiveTo;
	}
}
