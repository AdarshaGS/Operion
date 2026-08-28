package com.operion.organisation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

	Optional<AcademicYear> findByCurrentTrue();

	boolean existsByCurrentTrue();
}
