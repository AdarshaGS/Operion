package com.operion.academic;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SectionRepository extends JpaRepository<Section, Long> {

	List<Section> findBySchoolClassId(Long schoolClassId);

	/** Batch form of findBySchoolClassId - the promotion preview reads every target class's sections in one query. */
	List<Section> findBySchoolClassIdIn(Collection<Long> schoolClassIds);

	/** Duplicate check for bulk import (Classes & Sections) and class/section lookup for
	 * bulk Student Enrolments import - a section is named, not id-referenced, in a row. */
	Optional<Section> findBySchoolClassIdAndNameIgnoreCase(Long schoolClassId, String name);

	/** Row lock on the section, taken inside a transaction before its capacity / next roll number is
	 * read, so two concurrent placements into the same section serialise instead of both passing the check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Section s where s.id = :id")
	Optional<Section> findByIdForUpdate(@Param("id") Long id);
}
