package com.operion.organisation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {

	long countByStatus(CampusStatus status);
}
