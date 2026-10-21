package com.operion.examination;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

	List<ExamSchedule> findByExamId(Long examId);

	List<ExamSchedule> findByExamIdAndSchoolClassId(Long examId, Long schoolClassId);

	/** Schedules that apply to one student's section: whole-class rows (section IS NULL) plus any row scoped to this exact section. Per #139. */
	@Query("SELECT s FROM ExamSchedule s WHERE s.exam.id = :examId AND s.schoolClass.id = :schoolClassId "
			+ "AND (s.section IS NULL OR s.section.id = :sectionId)")
	List<ExamSchedule> findApplicableToSection(@Param("examId") Long examId, @Param("schoolClassId") Long schoolClassId, @Param("sectionId") Long sectionId);

	List<ExamSchedule> findByExamIdAndSchoolClassIdAndSubjectId(Long examId, Long schoolClassId, Long subjectId);

	/** Examinations overview - every schedule in one academic year, with what the rollup reads fetched up front. */
	@Query("SELECT s FROM ExamSchedule s JOIN FETCH s.exam e JOIN FETCH s.schoolClass c JOIN FETCH c.gradeLevel JOIN FETCH s.subject WHERE e.academicYear.id = :academicYearId")
	List<ExamSchedule> findForOverview(@Param("academicYearId") Long academicYearId);
}
