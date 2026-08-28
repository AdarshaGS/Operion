package com.operion.reporting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedReportShareRepository extends JpaRepository<SavedReportShare, Long> {

	List<SavedReportShare> findBySavedReportId(Long savedReportId);

	List<SavedReportShare> findByPrincipalTypeAndPrincipalId(SharePrincipalType principalType, Long principalId);

	List<SavedReportShare> findByPrincipalTypeAndPrincipalIdIn(SharePrincipalType principalType, List<Long> principalIds);
}
