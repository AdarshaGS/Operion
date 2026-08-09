package com.operion.parent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, Long> {

	List<StudentGuardian> findByStudentId(Long studentId);

	List<StudentGuardian> findByGuardianId(Long guardianId);

	Optional<StudentGuardian> findByStudentIdAndGuardianId(Long studentId, Long guardianId);

	Optional<StudentGuardian> findByStudentIdAndPrimaryGuardianTrue(Long studentId);
}
