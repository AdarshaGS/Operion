package com.operion.billing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

	List<Plan> findByStatus(PlanStatus status);
}
