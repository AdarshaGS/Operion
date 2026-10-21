package com.operion.learning;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonPlanRepository extends JpaRepository<LessonPlan, Long> {

	List<LessonPlan> findAllByOrderByPlanDateDescIdDesc();

	List<LessonPlan> findBySectionIdInOrderByPlanDateDescIdDesc(Collection<Long> sectionIds);

	List<LessonPlan> findBySectionIdAndStatusAndPlanDateBetweenOrderByPlanDateAsc(Long sectionId, LessonPlanStatus status,
			LocalDate from, LocalDate to);

	List<LessonPlan> findByStatusAndPlanDateBetweenOrderByPlanDateAsc(LessonPlanStatus status, LocalDate from, LocalDate to);

	List<LessonPlan> findByPlanDateBetween(LocalDate from, LocalDate to);
}
