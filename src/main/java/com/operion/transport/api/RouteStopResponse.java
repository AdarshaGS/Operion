package com.operion.transport.api;

import java.time.LocalTime;

import com.operion.transport.RouteStop;

public record RouteStopResponse(Long id, Long routeId, String stopName, int sequenceNumber, LocalTime pickupTime,
		LocalTime dropTime, Double latitude, Double longitude) {

	public static RouteStopResponse from(RouteStop routeStop) {
		return new RouteStopResponse(routeStop.getId(), routeStop.getRoute().getId(), routeStop.getStopName(),
				routeStop.getSequenceNumber(), routeStop.getPickupTime(), routeStop.getDropTime(),
				routeStop.getLatitude(), routeStop.getLongitude());
	}
}
