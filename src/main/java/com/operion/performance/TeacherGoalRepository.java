package com.operion.performance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherGoalRepository extends JpaRepository<TeacherGoal, Long> {

	List<TeacherGoal> findByCycleIdAndTeacherIdOrderByIdAsc(Long cycleId, Long teacherPersonId);

	/** Every goal in one cycle with its teacher loaded, for the cycle overview. */
	@Query("select g from TeacherGoal g join fetch g.teacher where g.cycle.id = :cycleId")
	List<TeacherGoal> findByCycleIdWithTeacher(@Param("cycleId") Long cycleId);
}
