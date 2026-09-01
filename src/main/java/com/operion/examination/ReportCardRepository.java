package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {

	Optional<ReportCard> findByExamIdAndStudentEnrollmentIdAndStatus(Long examId, Long studentEnrollmentId, ReportCardStatus status);

	List<ReportCard> findByStudentEnrollmentId(Long studentEnrollmentId);

	/** Every currently-PUBLISHED report card in one (exam, class) cohort - used to recompute class-wide rank on each (re)publish. Per #136. */
	List<ReportCard> findByExamIdAndStudentEnrollment_Section_SchoolClass_IdAndStatus(Long examId, Long schoolClassId, ReportCardStatus status);
}
