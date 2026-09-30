package com.operion.learning;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

	Optional<QuizAttempt> findByQuizIdAndStudentId(Long quizId, Long studentId);

	List<QuizAttempt> findByQuizId(Long quizId);

	List<QuizAttempt> findByQuizIdIn(Collection<Long> quizIds);

	List<QuizAttempt> findByStudentIdOrderBySubmittedAtDesc(Long studentId);
}
