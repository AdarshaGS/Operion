package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

	List<QuizQuestion> findByQuizIdOrderByOrdinalAsc(Long quizId);

	List<QuizQuestion> findByQuizIdIn(Collection<Long> quizIds);

	/** Bank items for a subject (quiz is null). */
	List<QuizQuestion> findByQuizIsNullAndSubjectIdOrderByIdDesc(Long subjectId);

	List<QuizQuestion> findByQuizIsNullOrderByIdDesc();
}
