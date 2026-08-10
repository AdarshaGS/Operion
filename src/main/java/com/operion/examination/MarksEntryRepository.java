package com.operion.examination;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarksEntryRepository extends JpaRepository<MarksEntry, Long> {

	Optional<MarksEntry> findByExamScheduleIdAndStudentEnrollmentId(Long examScheduleId, Long studentEnrollmentId);

	List<MarksEntry> findByExamScheduleId(Long examScheduleId);
}
