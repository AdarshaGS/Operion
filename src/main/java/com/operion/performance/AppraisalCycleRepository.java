package com.operion.performance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppraisalCycleRepository extends JpaRepository<AppraisalCycle, Long> {

	List<AppraisalCycle> findAllByOrderByStartDateDescIdDesc();

	boolean existsByNameIgnoreCase(String name);
}
