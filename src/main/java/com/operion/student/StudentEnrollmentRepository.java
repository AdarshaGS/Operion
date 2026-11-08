package com.operion.student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

	/** Row lock taken before any admission invoice is touched, so two payments against one student's admission
	 * invoices serialise and the clearance recalculation never reads a stale sibling invoice. Lock order is
	 * always enrollment first, then invoices in id order. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from StudentEnrollment e where e.id = :id")
	Optional<StudentEnrollment> findByIdForUpdate(@Param("id") Long id);

	Optional<StudentEnrollment> findByStudentIdAndCurrentTrue(Long studentId);

	/** Batch form of findByStudentIdAndCurrentTrue - used to enrich a page of students
	 * (#245) without one query per row. */
	List<StudentEnrollment> findByStudentIdInAndCurrentTrue(List<Long> studentIds);

	List<StudentEnrollment> findByStudentId(Long studentId);

	Optional<StudentEnrollment> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);

	List<StudentEnrollment> findBySectionId(Long sectionId);

	List<StudentEnrollment> findBySectionIdAndCurrentTrue(Long sectionId);

	/** Highest roll number already used in the section for the year (null when none) - the basis for
	 * auto-assigning the next one; callers hold the section row lock so two placements cannot read the same max. */
	@Query("SELECT MAX(e.rollNumber) FROM StudentEnrollment e WHERE e.section.id = :sectionId AND e.academicYear.id = :academicYearId")
	Integer findMaxRollNumber(@Param("sectionId") Long sectionId, @Param("academicYearId") Long academicYearId);

	/** True when any enrollment (current or closed) in the section already uses the roll number - the
	 * uq_student_enrollments_section_roll unique key spans all of them, not just current rows. */
	boolean existsBySectionIdAndRollNumber(Long sectionId, Integer rollNumber);

	boolean existsBySectionIdAndCurrentTrue(Long sectionId);

	boolean existsBySectionSchoolClassIdAndCurrentTrue(Long classId);

	List<StudentEnrollment> findByAcademicYearId(Long academicYearId);

	/** Students still enrolled (current) in the year - closing a year is refused while this is non-zero. */
	long countByAcademicYearIdAndCurrentTrue(Long academicYearId);

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

	/** Promotion preview - ACTIVE students' current enrollments in one year, with the student, person, section, class and
	 * grade fetched so the whole class list is one query. */
	@Query("SELECT e FROM StudentEnrollment e JOIN FETCH e.student st JOIN FETCH st.person "
			+ "JOIN FETCH e.section sec JOIN FETCH sec.schoolClass sc JOIN FETCH sc.gradeLevel "
			+ "WHERE e.academicYear.id = :academicYearId AND e.current = true "
			+ "AND st.status = com.operion.student.StudentStatus.ACTIVE")
	List<StudentEnrollment> findCurrentOfActiveStudentsInYear(@Param("academicYearId") Long academicYearId);

	/** Promotion preview - which of the given students already hold an enrollment (any status) in the year. */
	List<StudentEnrollment> findByAcademicYearIdAndStudentIdIn(Long academicYearId, Collection<Long> studentIds);

	/** Promotion preview - current head-count for several sections at once. Rows are (section id, count). */
	@Query("SELECT e.section.id, COUNT(e) FROM StudentEnrollment e "
			+ "WHERE e.current = true AND e.section.id IN :sectionIds GROUP BY e.section.id")
	List<Object[]> countCurrentBySectionIds(@Param("sectionIds") Collection<Long> sectionIds);
}
