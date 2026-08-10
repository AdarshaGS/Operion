package com.operion.transport.api;

public record CreateVehicleRequest(Long campusId, String registrationNumber, String vehicleType, int capacity,
		Long driverPersonId, Long attendantPersonId) {
}
