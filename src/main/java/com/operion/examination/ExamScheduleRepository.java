package com.operion.examination;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

	List<ExamSchedule> findByExamId(Long examId);

	List<ExamSchedule> findByExamIdAndSchoolClassId(Long examId, Long schoolClassId);
}
