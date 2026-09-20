package com.operion.organisation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, Long> {

	Optional<Designation> findByNameIgnoreCase(String name);
}
