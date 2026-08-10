package com.operion.transport.api;

import java.time.LocalDate;

public record ScheduleTripRequest(Long routeId, Long vehicleId, Long driverPersonId, LocalDate tripDate, String tripType) {
}
