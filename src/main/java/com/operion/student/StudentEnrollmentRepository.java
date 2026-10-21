package com.operion.student;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

	Optional<StudentEnrollment> findByStudentIdAndCurrentTrue(Long studentId);

	/** Batch form of findByStudentIdAndCurrentTrue - used to enrich a page of students
	 * (#245) without one query per row. */
	List<StudentEnrollment> findByStudentIdInAndCurrentTrue(List<Long> studentIds);

	List<StudentEnrollment> findByStudentId(Long studentId);

	Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);

	List<StudentEnrollment> findBySectionId(Long sectionId);

	List<StudentEnrollment> findBySectionIdAndCurrentTrue(Long sectionId);

	boolean existsBySectionIdAndCurrentTrue(Long sectionId);

	boolean existsBySectionSchoolClassIdAndCurrentTrue(Long classId);

	List<StudentEnrollment> findByAcademicYearId(Long academicYearId);

	/** Dashboard students-by-class chart - current ENROLLED students per class, ordered by grade
	 * sequence. Rows are (class display name or grade name, student count). */
	@Query("SELECT COALESCE(sc.displayName, g.name), COUNT(e) FROM StudentEnrollment e "
			+ "JOIN e.section s JOIN s.schoolClass sc JOIN sc.gradeLevel g "
			+ "WHERE e.current = true AND e.enrollmentStatus = com.operion.student.StudentEnrollmentStatus.ENROLLED "
			+ "GROUP BY sc.id, sc.displayName, g.name, g.sequenceOrder ORDER BY g.sequenceOrder, sc.displayName")
	List<Object[]> countCurrentEnrolledBySchoolClass();

	/** Attendance overview - enrolled head-count per section: (section id, section name, class id, class label, grade order, count). */
	@Query("SELECT s.id, s.name, sc.id, COALESCE(sc.displayName, g.name), g.sequenceOrder, COUNT(e) FROM StudentEnrollment e "
			+ "JOIN e.section s JOIN s.schoolClass sc JOIN sc.gradeLevel g "
			+ "WHERE e.current = true AND e.enrollmentStatus = com.operion.student.StudentEnrollmentStatus.ENROLLED "
			+ "GROUP BY s.id, s.name, sc.id, sc.displayName, g.name, g.sequenceOrder")
	List<Object[]> countCurrentEnrolledBySection();
}
