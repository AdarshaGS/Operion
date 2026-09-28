package com.operion.transport;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The driver-specific extension of Person, per #155 - a bare Person shouldn't grow
 * licence columns directly, same "role-specific profile FKs to Person" precedent as
 * StaffProfile/Student/Guardian. 1:1 with Person, upserted as a whole on renewal - same
 * "one row, update in place" convention as StaffBankDetail, since a licence renewal is
 * a correction to the current record, not history worth preserving row-by-row (unlike
 * VehicleDocument's file re-uploads). Deliberately not FK'd to Vehicle - Vehicle.driver
 * already holds that relationship, and a driver's licence outlives any one vehicle
 * assignment.
 */
@Getter
@Entity
@Table(name = "driver_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverProfile extends TenantScopedEntity {

	@OneToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(name = "licence_number", nullable = false)
	private String licenceNumber;

	@Column(name = "licence_expiry_date", nullable = false)
	private LocalDate licenceExpiryDate;

	public DriverProfile(Person person, String licenceNumber, LocalDate licenceExpiryDate) {
		this.person = person;
		update(licenceNumber, licenceExpiryDate);
	}

	public void update(String licenceNumber, LocalDate licenceExpiryDate) {
		this.licenceNumber = licenceNumber;
		this.licenceExpiryDate = licenceExpiryDate;
	}
}
