package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

	List<QuizOption> findByQuestionIdOrderByOrdinalAsc(Long questionId);

	List<QuizOption> findByQuestionIdIn(Collection<Long> questionIds);
}
