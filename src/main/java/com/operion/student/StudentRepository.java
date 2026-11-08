package com.operion.student;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

	Optional<Student> findByPersonId(Long personId);

	List<Student> findByPersonIdIn(Collection<Long> personIds);

	Optional<Student> findByAdmissionNumber(String admissionNumber);

	long countByStatus(StudentStatus status);

	List<Student> findByStatusIn(Collection<StudentStatus> statuses);

	/** Every filter is optional (null = "don't filter on this") - #245's search/filter
	 * list. Joins to the student's *current* enrollment/section only, so class/section
	 * filters mean "currently placed there", not "ever placed there". The campus filter
	 * is the campus of that current section's class, so with a campusId, students with no
	 * current enrollment drop out; with null every join stays LEFT and they are kept. */
	@Query(value = "SELECT DISTINCT s FROM Student s JOIN FETCH s.person p "
			+ "LEFT JOIN StudentEnrollment se ON se.student = s AND se.current = true "
			+ "LEFT JOIN se.section sec "
			+ "LEFT JOIN sec.schoolClass sc "
			+ "WHERE (:search IS NULL OR LOWER(p.firstName) LIKE :search OR LOWER(p.lastName) LIKE :search OR LOWER(s.admissionNumber) LIKE :search) "
			+ "AND (:status IS NULL OR s.status = :status) "
			+ "AND (:schoolClassId IS NULL OR sec.schoolClass.id = :schoolClassId) "
			+ "AND (:sectionId IS NULL OR sec.id = :sectionId) "
			+ "AND (:campusId IS NULL OR sc.campus.id = :campusId) "
			+ "AND (:admissionDateFrom IS NULL OR s.admissionDate >= :admissionDateFrom) "
			+ "AND (:admissionDateTo IS NULL OR s.admissionDate <= :admissionDateTo)",
			countQuery = "SELECT COUNT(DISTINCT s) FROM Student s JOIN s.person p "
					+ "LEFT JOIN StudentEnrollment se ON se.student = s AND se.current = true "
					+ "LEFT JOIN se.section sec "
					+ "LEFT JOIN sec.schoolClass sc "
					+ "WHERE (:search IS NULL OR LOWER(p.firstName) LIKE :search OR LOWER(p.lastName) LIKE :search OR LOWER(s.admissionNumber) LIKE :search) "
					+ "AND (:status IS NULL OR s.status = :status) "
					+ "AND (:schoolClassId IS NULL OR sec.schoolClass.id = :schoolClassId) "
					+ "AND (:sectionId IS NULL OR sec.id = :sectionId) "
					+ "AND (:campusId IS NULL OR sc.campus.id = :campusId) "
					+ "AND (:admissionDateFrom IS NULL OR s.admissionDate >= :admissionDateFrom) "
					+ "AND (:admissionDateTo IS NULL OR s.admissionDate <= :admissionDateTo)")
	Page<Student> search(@Param("search") String search, @Param("status") StudentStatus status,
			@Param("schoolClassId") Long schoolClassId, @Param("sectionId") Long sectionId,
			@Param("campusId") Long campusId, @Param("admissionDateFrom") LocalDate admissionDateFrom, @Param("admissionDateTo") LocalDate admissionDateTo,
			Pageable pageable);

	/** The pre-campus signature: no campus filter (campusId = null). */
	default Page<Student> search(String search, StudentStatus status, Long schoolClassId, Long sectionId,
			LocalDate admissionDateFrom, LocalDate admissionDateTo, Pageable pageable) {
		return search(search, status, schoolClassId, sectionId, null, admissionDateFrom, admissionDateTo, pageable);
	}
}
