package com.operion.transport.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.transport.TripLog;

public record TripLogResponse(Long id, Long routeId, Long vehicleId, Long driverPersonId, LocalDate tripDate,
		String tripType, String status, Instant startedAt, Instant completedAt, String remarks) {

	public static TripLogResponse from(TripLog tripLog) {
		return new TripLogResponse(tripLog.getId(), tripLog.getRoute().getId(), tripLog.getVehicle().getId(),
				tripLog.getDriver() == null ? null : tripLog.getDriver().getId(), tripLog.getTripDate(),
				tripLog.getTripType().name(), tripLog.getStatus().name(), tripLog.getStartedAt(), tripLog.getCompletedAt(),
				tripLog.getRemarks());
	}
}
