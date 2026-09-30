package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

	List<Quiz> findAllByOrderByOpensAtDescIdDesc();

	List<Quiz> findBySectionIdInOrderByOpensAtDescIdDesc(Collection<Long> sectionIds);

	List<Quiz> findBySectionIdAndStatusOrderByOpensAtDescIdDesc(Long sectionId, QuizStatus status);
}
