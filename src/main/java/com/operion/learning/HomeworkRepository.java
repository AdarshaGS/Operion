package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkRepository extends JpaRepository<Homework, Long> {

	List<Homework> findBySectionIdOrderByDueDateDescIdDesc(Long sectionId);

	List<Homework> findBySectionIdInOrderByDueDateDescIdDesc(Collection<Long> sectionIds);

	List<Homework> findAllByOrderByDueDateDescIdDesc();
}
