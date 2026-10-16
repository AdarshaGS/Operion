package com.operion.billing;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface PlanRepository extends JpaRepository<Plan, Long> {

	List<Plan> findByStatus(PlanStatus status);
}
