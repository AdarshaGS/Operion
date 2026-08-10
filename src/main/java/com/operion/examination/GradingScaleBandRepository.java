package com.operion.examination;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradingScaleBandRepository extends JpaRepository<GradingScaleBand, Long> {

	List<GradingScaleBand> findByGradingScaleIdOrderByMinPercentageDesc(Long gradingScaleId);
}
