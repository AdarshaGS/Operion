package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarksEntryRegisterRepository extends JpaRepository<MarksEntryRegister, Long> {

	Optional<MarksEntryRegister> findByExamScheduleId(Long examScheduleId);

	/** Examinations overview - every marks register in one academic year. */
	@Query("SELECT r FROM MarksEntryRegister r JOIN FETCH r.examSchedule s WHERE s.exam.academicYear.id = :academicYearId")
	List<MarksEntryRegister> findForOverview(@Param("academicYearId") Long academicYearId);
}
