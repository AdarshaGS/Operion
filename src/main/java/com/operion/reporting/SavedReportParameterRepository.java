package com.operion.reporting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReportParameterRepository extends JpaRepository<SavedReportParameter, Long> {

	List<SavedReportParameter> findBySavedReportIdOrderBySortOrder(Long savedReportId);
}
