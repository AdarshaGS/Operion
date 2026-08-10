package com.operion.transport.api;

import com.operion.transport.Route;

public record RouteResponse(Long id, Long campusId, String name, String code, Long vehicleId, String status) {

	public static RouteResponse from(Route route) {
		return new RouteResponse(route.getId(), route.getCampus().getId(), route.getName(), route.getCode(),
				route.getVehicle() == null ? null : route.getVehicle().getId(), route.getStatus().name());
	}
}
