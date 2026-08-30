package com.operion.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

	Optional<StudentEnrollment> findByStudentIdAndCurrentTrue(Long studentId);

	/** Batch form of findByStudentIdAndCurrentTrue - used to enrich a page of students
	 * (#245) without one query per row. */
	List<StudentEnrollment> findByStudentIdInAndCurrentTrue(List<Long> studentIds);

	List<StudentEnrollment> findByStudentId(Long studentId);

	List<StudentEnrollment> findBySectionId(Long sectionId);

	List<StudentEnrollment> findBySectionIdAndCurrentTrue(Long sectionId);

	List<StudentEnrollment> findByAcademicYearId(Long academicYearId);
}
