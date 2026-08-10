package com.operion.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

	Optional<StudentEnrollment> findByStudentIdAndCurrentTrue(Long studentId);

	List<StudentEnrollment> findByStudentId(Long studentId);

	List<StudentEnrollment> findBySectionId(Long sectionId);

	List<StudentEnrollment> findBySectionIdAndCurrentTrue(Long sectionId);

	List<StudentEnrollment> findByAcademicYearId(Long academicYearId);
}
