package com.operion.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

	List<Route> findByCampusId(Long campusId);

	long countByStatus(RouteStatus status);

	Optional<Route> findByCodeIgnoreCase(String code);
}
