package com.operion.organisation;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

	Optional<AcademicYear> findByCurrentTrue();

	boolean existsByCurrentTrue();

	/** Cheap "has any year reached this status" probe - feeds the dashboard setup checklist (closed-year signal). */
	boolean existsByStatus(AcademicYearStatus status);

	/** Dashboard setup checklist (subject configuration) - the most recently closed year when no year is current:
	 * latest end date, ties broken by the higher id. */
	Optional<AcademicYear> findFirstByStatusOrderByEndDateDescIdDesc(AcademicYearStatus status);

	Optional<AcademicYear> findByNameIgnoreCase(String name);

	boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);
}
