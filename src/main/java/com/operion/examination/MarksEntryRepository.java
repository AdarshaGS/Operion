package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarksEntryRepository extends JpaRepository<MarksEntry, Long> {

	Optional<MarksEntry> findByExamScheduleIdAndStudentEnrollmentId(Long examScheduleId, Long studentEnrollmentId);

	List<MarksEntry> findByExamScheduleId(Long examScheduleId);

	/** All marks of the students currently or previously enrolled in one section - feeds the learning leaderboard (#231). */
	List<MarksEntry> findByStudentEnrollmentSectionId(Long sectionId);

	List<MarksEntry> findByExamScheduleExamDateBetween(java.time.LocalDate from, java.time.LocalDate to);
}
