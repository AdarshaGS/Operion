package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

	List<Subject> findByStatus(SubjectStatus status);

	/** Duplicate check for bulk import (Subjects) - a subject's code is its natural key
	 * for that purpose. */
	Optional<Subject> findByCodeIgnoreCase(String code);
}
