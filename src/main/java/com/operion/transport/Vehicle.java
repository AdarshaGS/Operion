package com.operion.transport;

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
 * Fleet registry, campus-scoped. driver/attendant are bare Person FKs, not required to
 * hold a Staff/OrganisationMembership profile - many schools use contracted drivers
 * with no ERP login, per the design sign-off. Never hard-deleted; TripLog references a
 * vehicle historically even after it's RETIRED.
 */
@Getter
@Entity
@Table(name = "vehicles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "registration_number", nullable = false)
	private String registrationNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "vehicle_type", nullable = false, length = 20)
	private VehicleType vehicleType;

	@Column(nullable = false)
	private int capacity;

	/** Nullable - vehicle may temporarily have no assigned driver. */
	@ManyToOne
	@JoinColumn(name = "driver_person_id")
	private Person driver;

	/** Nullable. */
	@ManyToOne
	@JoinColumn(name = "attendant_person_id")
	private Person attendant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleStatus status;

	public Vehicle(Campus campus, String registrationNumber, VehicleType vehicleType, int capacity, Person driver, Person attendant) {
		this.campus = campus;
		this.registrationNumber = registrationNumber;
		this.vehicleType = vehicleType;
		this.capacity = capacity;
		this.driver = driver;
		this.attendant = attendant;
		this.status = VehicleStatus.ACTIVE;
	}

	public void reassignCrew(Person driver, Person attendant) {
		this.driver = driver;
		this.attendant = attendant;
	}

	public void changeStatus(VehicleStatus status) {
		this.status = status;
	}
}
