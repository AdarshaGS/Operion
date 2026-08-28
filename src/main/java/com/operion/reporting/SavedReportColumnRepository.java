package com.operion.reporting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReportColumnRepository extends JpaRepository<SavedReportColumn, Long> {

	List<SavedReportColumn> findBySavedReportIdOrderBySortOrder(Long savedReportId);
}
