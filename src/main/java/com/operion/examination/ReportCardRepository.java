package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {

	Optional<ReportCard> findByExamIdAndStudentEnrollmentIdAndStatus(Long examId, Long studentEnrollmentId, ReportCardStatus status);

	List<ReportCard> findByStudentEnrollmentId(Long studentEnrollmentId);

	/** Every currently-PUBLISHED report card in one (exam, class) cohort - used to recompute class-wide rank on each (re)publish. Per #136. */
	List<ReportCard> findByExamIdAndStudentEnrollment_Section_SchoolClass_IdAndStatus(Long examId, Long schoolClassId, ReportCardStatus status);

	/** One section's report cards for an exam - the class-teacher "print the whole section" PDF. */
	List<ReportCard> findByExamIdAndStudentEnrollment_Section_IdAndStatus(Long examId, Long sectionId, ReportCardStatus status);

	/** Examinations overview - the report cards currently in force for one academic year's exams. */
	@Query("SELECT r FROM ReportCard r JOIN FETCH r.exam e WHERE e.academicYear.id = :academicYearId AND r.status = :status")
	List<ReportCard> findForOverview(@Param("academicYearId") Long academicYearId, @Param("status") ReportCardStatus status);
}
