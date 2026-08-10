package com.operion.transport;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripLogRepository extends JpaRepository<TripLog, Long> {

	List<TripLog> findByRouteIdAndTripDate(Long routeId, LocalDate tripDate);
}
