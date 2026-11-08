package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

	List<SchoolClass> findByAcademicYearId(Long academicYearId);

	/** Get-or-create lookup for bulk import (Classes & Sections) - a class is identified
	 * by which year/campus/grade offering it is, not its display name. */
	Optional<SchoolClass> findByAcademicYearIdAndCampusIdAndGradeLevelId(
			Long academicYearId, Long campusId, Long gradeLevelId);

	/** Resolves a class by its display name within a year - used by bulk Student
	 * Enrolments import, which references a class by name, not id. */
	Optional<SchoolClass> findByAcademicYearIdAndDisplayNameIgnoreCase(Long academicYearId, String displayName);

	/** Dashboard setup checklist (subject configuration) - ACTIVE classes of one academic year. */
	@Query("SELECT COUNT(sc) FROM SchoolClass sc WHERE sc.academicYear.id = :academicYearId "
			+ "AND sc.status = com.operion.academic.SchoolClassStatus.ACTIVE")
	long countActiveInAcademicYear(@Param("academicYearId") Long academicYearId);

	/** Dashboard setup checklist (subject configuration) - how many of those classes still have no ACTIVE
	 * class-subject mapping. A single count, never the rows. */
	@Query("SELECT COUNT(sc) FROM SchoolClass sc WHERE sc.academicYear.id = :academicYearId "
			+ "AND sc.status = com.operion.academic.SchoolClassStatus.ACTIVE "
			+ "AND NOT EXISTS (SELECT 1 FROM ClassSubject cs WHERE cs.schoolClass = sc "
			+ "AND cs.status = com.operion.academic.ClassSubjectStatus.ACTIVE)")
	long countActiveInAcademicYearWithoutActiveSubject(@Param("academicYearId") Long academicYearId);
}
