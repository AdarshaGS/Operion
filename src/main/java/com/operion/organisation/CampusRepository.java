package com.operion.organisation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {

	long countByStatus(CampusStatus status);

	Optional<Campus> findByNameIgnoreCase(String name);
}
