package com.operion.learning;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerSheetAnnotationRepository extends JpaRepository<AnswerSheetAnnotation, Long> {

	List<AnswerSheetAnnotation> findByPageSheetId(Long sheetId);

	void deleteByPageSheetId(Long sheetId);
}
