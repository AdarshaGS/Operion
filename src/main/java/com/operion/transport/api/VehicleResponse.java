package com.operion.transport.api;

import com.operion.transport.Vehicle;

public record VehicleResponse(Long id, Long campusId, String registrationNumber, String vehicleType, int capacity,
		Long driverPersonId, Long attendantPersonId, String status) {

	public static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(vehicle.getId(), vehicle.getCampus().getId(), vehicle.getRegistrationNumber(),
				vehicle.getVehicleType().name(), vehicle.getCapacity(),
				vehicle.getDriver() == null ? null : vehicle.getDriver().getId(),
				vehicle.getAttendant() == null ? null : vehicle.getAttendant().getId(),
				vehicle.getStatus().name());
	}
}
