package com.operion.transport;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	List<Vehicle> findByCampusId(Long campusId);

	long countByStatus(VehicleStatus status);

	Optional<Vehicle> findByRegistrationNumberIgnoreCase(String registrationNumber);
}
