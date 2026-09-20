package com.operion.transport.api;

import com.operion.identity.Person;
import com.operion.transport.Vehicle;

/** Flat one-row-per-vehicle export - routes have their own list/detail endpoints on
 * RouteController, so a route-specific export isn't duplicated here. */
public record VehicleExportResponse(Long id, String registrationNumber, String vehicleType, int capacity, String campusName,
		String driverName, String attendantName, String status) {

	public static VehicleExportResponse from(Vehicle vehicle) {
		return new VehicleExportResponse(vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getVehicleType().name(),
				vehicle.getCapacity(), vehicle.getCampus().getName(), fullName(vehicle.getDriver()), fullName(vehicle.getAttendant()),
				vehicle.getStatus().name());
	}

	private static String fullName(Person person) {
		if (person == null) {
			return null;
		}
		return person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
	}
}
