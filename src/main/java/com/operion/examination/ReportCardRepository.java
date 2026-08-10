package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {

	Optional<ReportCard> findByExamIdAndStudentEnrollmentId(Long examId, Long studentEnrollmentId);

	List<ReportCard> findByStudentEnrollmentId(Long studentEnrollmentId);
}
