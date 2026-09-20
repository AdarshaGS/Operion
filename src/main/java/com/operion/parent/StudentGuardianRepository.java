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

	/** Bulk import duplicate check (GuardianRowImportService) - a link already exists
	 * between this student and a guardian whose Person has this email/phone. */
	Optional<StudentGuardian> findByStudentIdAndGuardian_Person_Email(Long studentId, String email);

	Optional<StudentGuardian> findByStudentIdAndGuardian_Person_Phone(Long studentId, String phone);
}
