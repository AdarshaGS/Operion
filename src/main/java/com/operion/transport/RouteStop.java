package com.operion.transport;

import java.time.LocalTime;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An ordered stop on a route. latitude/longitude are nullable seam columns for a
 * future map view - not live tracking, just a fixed point.
 */
@Getter
@Entity
@Table(name = "route_stops", uniqueConstraints = @UniqueConstraint(columnNames = { "route_id", "sequence_number" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStop extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "route_id")
	private Route route;

	@Column(name = "stop_name", nullable = false)
	private String stopName;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	/** Nullable - not every stop has both a pickup and a drop leg. */
	@Column(name = "pickup_time")
	private LocalTime pickupTime;

	@Column(name = "drop_time")
	private LocalTime dropTime;

	private Double latitude;

	private Double longitude;

	public RouteStop(Route route, String stopName, int sequenceNumber, LocalTime pickupTime, LocalTime dropTime,
			Double latitude, Double longitude) {
		this.route = route;
		this.stopName = stopName;
		this.sequenceNumber = sequenceNumber;
		this.pickupTime = pickupTime;
		this.dropTime = dropTime;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
