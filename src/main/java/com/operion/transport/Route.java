package com.operion.transport;

import com.operion.common.TenantScopedEntity;
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
 * A named route, campus-scoped. vehicle is nullable and mutable - a breakdown means
 * swapping the covering vehicle without losing route/stop history. Not versioned; ad
 * hoc swaps rely on the shared AuditLog, same precedent as
 * StudentEnrollment.reassignSection.
 */
@Getter
@Entity
@Table(name = "routes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	/** Nullable - a route can exist before a vehicle is assigned to run it. */
	@ManyToOne
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RouteStatus status;

	public Route(Campus campus, String name, String code, Vehicle vehicle) {
		this.campus = campus;
		this.name = name;
		this.code = code;
		this.vehicle = vehicle;
		this.status = RouteStatus.ACTIVE;
	}

	public void assignVehicle(Vehicle vehicle) {
		this.vehicle = vehicle;
	}

	public void changeStatus(RouteStatus status) {
		this.status = status;
	}
}
