package com.operion.learning;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerSheetPageRepository extends JpaRepository<AnswerSheetPage, Long> {

	List<AnswerSheetPage> findBySheetIdOrderByPageNumber(Long sheetId);

	void deleteBySheetId(Long sheetId);
}
