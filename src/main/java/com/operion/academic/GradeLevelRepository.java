package com.operion.academic;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, Long> {

	/** Get-or-create lookup for bulk import (Classes & Sections) - a row names its grade
	 * level, not its id. */
	Optional<GradeLevel> findByNameIgnoreCase(String name);
}
