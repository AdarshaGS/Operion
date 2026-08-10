package com.operion.examination;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {

	List<Exam> findByAcademicYearId(Long academicYearId);
}
