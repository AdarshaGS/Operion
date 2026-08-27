package com.operion.transport;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	List<Vehicle> findByCampusId(Long campusId);

	long countByStatus(VehicleStatus status);
}
