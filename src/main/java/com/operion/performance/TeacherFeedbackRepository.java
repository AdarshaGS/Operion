package com.operion.performance;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherFeedbackRepository extends JpaRepository<TeacherFeedback, Long> {

	List<TeacherFeedback> findByCycleIdAndTeacherId(Long cycleId, Long teacherPersonId);

	Optional<TeacherFeedback> findByCycleIdAndTeacherIdAndReviewerId(Long cycleId, Long teacherPersonId, Long reviewerPersonId);

	/** Every rating in one cycle with its teacher loaded, for the cycle overview. */
	@Query("select f from TeacherFeedback f join fetch f.teacher where f.cycle.id = :cycleId")
	List<TeacherFeedback> findByCycleIdWithTeacher(@Param("cycleId") Long cycleId);

	/** Who has been rated by whom (by source) in every cycle - ids only, for the cycles list. */
	@Query("select new com.operion.performance.TeacherFeedbackRepository$RatedTeacher(f.cycle.id, f.teacher.id, f.source) from TeacherFeedback f")
	List<RatedTeacher> findAllRatedTeachers();

	record RatedTeacher(Long cycleId, Long teacherId, FeedbackSource source) {
	}

	List<TeacherFeedback> findBySourceInAndCreatedAtBetween(Collection<FeedbackSource> sources, Instant from, Instant to);
}
