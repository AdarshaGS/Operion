package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerSheetRepository extends JpaRepository<AnswerSheet, Long> {

	List<AnswerSheet> findByExamScheduleId(Long examScheduleId);

	List<AnswerSheet> findByExamScheduleIdAndStudentEnrollmentIdIn(Long examScheduleId, Collection<Long> enrollmentIds);

	java.util.Optional<AnswerSheet> findByExamScheduleIdAndStudentEnrollmentId(Long examScheduleId, Long enrollmentId);

	List<AnswerSheet> findByStudentEnrollmentStudentIdAndStatusOrderByReleasedAtDesc(Long studentId, AnswerSheetStatus status);

	List<AnswerSheet> findByStatusAndReleasedAtBetween(AnswerSheetStatus status, java.time.Instant from, java.time.Instant to);
}
