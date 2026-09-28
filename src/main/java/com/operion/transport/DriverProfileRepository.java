package com.operion.transport;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

	Optional<DriverProfile> findByPersonId(Long personId);
}
