package com.operion.parent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {

	List<StudentGuardian> findByStudentId(Long studentId);

	List<StudentGuardian> findByGuardianId(Long guardianId);

	Optional<StudentGuardian> findByStudentIdAndGuardianId(Long studentId, Long guardianId);

	Optional<StudentGuardian> findByStudentIdAndPrimaryGuardianTrue(Long studentId);

	/** Batch form - used to enrich a page of students with their primary guardian's
	 * contact (#245) without one query per row. */
	List<StudentGuardian> findByStudentIdInAndPrimaryGuardianTrueAndStatus(List<Long> studentIds, StudentGuardianStatus status);
}
