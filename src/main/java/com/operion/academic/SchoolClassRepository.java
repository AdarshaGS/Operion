package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

	List<SchoolClass> findByAcademicYearId(Long academicYearId);

	/** Get-or-create lookup for bulk import (Classes & Sections) - a class is identified
	 * by which year/campus/grade offering it is, not its display name. */
	Optional<SchoolClass> findByAcademicYearIdAndCampusIdAndGradeLevelId(
			Long academicYearId, Long campusId, Long gradeLevelId);

	/** Resolves a class by its display name within a year - used by bulk Student
	 * Enrolments import, which references a class by name, not id. */
	Optional<SchoolClass> findByAcademicYearIdAndDisplayNameIgnoreCase(Long academicYearId, String displayName);
}
