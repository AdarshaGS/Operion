package com.operion.transport.api;

public record CreateRouteRequest(Long campusId, String name, String code, Long vehicleId) {
}
