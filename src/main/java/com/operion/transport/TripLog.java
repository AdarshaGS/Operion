package com.operion.transport;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
 * A trip actually run on a route - header-only for v1 (no per-student boarding record;
 * that's a StudentTripAttendance seam deferred to v2, same split as
 * ClassAttendanceRegister/StudentAttendance, per the design sign-off). vehicle and
 * driver are snapshotted at creation, not read live off Route/Vehicle, since the
 * covering vehicle/driver on a given day may differ from the route's current default
 * (substitute driver, breakdown cover).
 */
@Getter
@Entity
@Table(name = "trip_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripLog extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "route_id")
	private Route route;

	@ManyToOne(optional = false)
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	/** Nullable - a driver may not yet be on record when a trip is scheduled. */
	@ManyToOne
	@JoinColumn(name = "driver_person_id")
	private Person driver;

	@Column(name = "trip_date", nullable = false)
	private LocalDate tripDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "trip_type", nullable = false, length = 20)
	private TripType tripType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TripStatus status;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	/** Nullable. */
	private String remarks;

	public TripLog(Route route, Vehicle vehicle, Person driver, LocalDate tripDate, TripType tripType) {
		this.route = route;
		this.vehicle = vehicle;
		this.driver = driver;
		this.tripDate = tripDate;
		this.tripType = tripType;
		this.status = TripStatus.SCHEDULED;
	}

	public void start() {
		if (status != TripStatus.SCHEDULED) {
			throw new IllegalStateException("Only a scheduled trip can be started, was " + status);
		}
		this.status = TripStatus.IN_PROGRESS;
		this.startedAt = Instant.now();
	}

	public void complete(String remarks) {
		if (status != TripStatus.IN_PROGRESS) {
			throw new IllegalStateException("Only an in-progress trip can be completed, was " + status);
		}
		this.status = TripStatus.COMPLETED;
		this.completedAt = Instant.now();
		this.remarks = remarks;
	}

	public void cancel(String remarks) {
		if (status == TripStatus.COMPLETED || status == TripStatus.CANCELLED) {
			throw new IllegalStateException("Cannot cancel a trip that is already " + status);
		}
		this.status = TripStatus.CANCELLED;
		this.remarks = remarks;
	}
}
