package com.operion.reporting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {

	List<SavedReport> findByCreatedBy(Long createdBy);

	boolean existsByName(String name);
}
