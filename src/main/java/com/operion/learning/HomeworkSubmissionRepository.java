package com.operion.learning;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, Long> {

	List<HomeworkSubmission> findByHomeworkId(Long homeworkId);

	List<HomeworkSubmission> findByHomeworkIdIn(Collection<Long> homeworkIds);

	Optional<HomeworkSubmission> findByHomeworkIdAndStudentId(Long homeworkId, Long studentId);

	List<HomeworkSubmission> findByStudentId(Long studentId);
}
