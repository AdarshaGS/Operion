package com.operion.transport;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

	List<Route> findByCampusId(Long campusId);
}
