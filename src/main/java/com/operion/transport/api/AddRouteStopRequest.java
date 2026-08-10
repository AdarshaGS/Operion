package com.operion.transport.api;

import java.time.LocalTime;

public record AddRouteStopRequest(String stopName, int sequenceNumber, LocalTime pickupTime, LocalTime dropTime,
		Double latitude, Double longitude) {
}
