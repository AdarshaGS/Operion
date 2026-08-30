package com.operion.student;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

	Optional<Student> findByPersonId(Long personId);

	Optional<Student> findByAdmissionNumber(String admissionNumber);

	long countByStatus(StudentStatus status);

	/** Every filter is optional (null = "don't filter on this") - #245's search/filter
	 * list. Joins to the student's *current* enrollment/section only, so class/section
	 * filters mean "currently placed there", not "ever placed there". */
	@Query(value = "SELECT DISTINCT s FROM Student s JOIN FETCH s.person p "
			+ "LEFT JOIN StudentEnrollment se ON se.student = s AND se.current = true "
			+ "LEFT JOIN se.section sec "
			+ "WHERE (:search IS NULL OR LOWER(p.firstName) LIKE :search OR LOWER(p.lastName) LIKE :search OR LOWER(s.admissionNumber) LIKE :search) "
			+ "AND (:status IS NULL OR s.status = :status) "
			+ "AND (:schoolClassId IS NULL OR sec.schoolClass.id = :schoolClassId) "
			+ "AND (:sectionId IS NULL OR sec.id = :sectionId) "
			+ "AND (:admissionDateFrom IS NULL OR s.admissionDate >= :admissionDateFrom) "
			+ "AND (:admissionDateTo IS NULL OR s.admissionDate <= :admissionDateTo)",
			countQuery = "SELECT COUNT(DISTINCT s) FROM Student s JOIN s.person p "
					+ "LEFT JOIN StudentEnrollment se ON se.student = s AND se.current = true "
					+ "LEFT JOIN se.section sec "
					+ "WHERE (:search IS NULL OR LOWER(p.firstName) LIKE :search OR LOWER(p.lastName) LIKE :search OR LOWER(s.admissionNumber) LIKE :search) "
					+ "AND (:status IS NULL OR s.status = :status) "
					+ "AND (:schoolClassId IS NULL OR sec.schoolClass.id = :schoolClassId) "
					+ "AND (:sectionId IS NULL OR sec.id = :sectionId) "
					+ "AND (:admissionDateFrom IS NULL OR s.admissionDate >= :admissionDateFrom) "
					+ "AND (:admissionDateTo IS NULL OR s.admissionDate <= :admissionDateTo)")
	Page<Student> search(@Param("search") String search, @Param("status") StudentStatus status,
			@Param("schoolClassId") Long schoolClassId, @Param("sectionId") Long sectionId,
			@Param("admissionDateFrom") LocalDate admissionDateFrom, @Param("admissionDateTo") LocalDate admissionDateTo,
			Pageable pageable);
}
