package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {

	List<Section> findBySchoolClassId(Long schoolClassId);

	/** Duplicate check for bulk import (Classes & Sections) and class/section lookup for
	 * bulk Student Enrolments import - a section is named, not id-referenced, in a row. */
	Optional<Section> findBySchoolClassIdAndNameIgnoreCase(Long schoolClassId, String name);
}
