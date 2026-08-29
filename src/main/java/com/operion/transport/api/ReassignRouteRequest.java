package com.operion.transport.api;

import java.time.LocalDate;

public record ReassignRouteRequest(Long routeId, Long routeStopId, LocalDate effectiveFrom) {
}
