package com.operion.transport;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

	List<RouteStop> findByRouteIdOrderBySequenceNumber(Long routeId);
}
