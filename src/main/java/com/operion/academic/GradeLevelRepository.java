package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, Long> {

	/** Get-or-create lookup for bulk import (Classes & Sections) - a row names its grade
	 * level, not its id. */
	Optional<GradeLevel> findByNameIgnoreCase(String name);

	List<GradeLevel> findByStatusOrderBySequenceOrderAsc(GradeLevelStatus status);

	/** The grade with the given status that follows the given sequence order - sequence_order may have gaps, so this is
	 * the smallest order above it, not order + 1. Callers pass ACTIVE so an inactive grade is never the next one.
	 * Empty when the given order is the last grade. */
	Optional<GradeLevel> findFirstByStatusAndSequenceOrderGreaterThanOrderBySequenceOrderAsc(GradeLevelStatus status, int sequenceOrder);
}
