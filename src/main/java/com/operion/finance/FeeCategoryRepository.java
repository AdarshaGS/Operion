package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeCategoryRepository extends JpaRepository<FeeCategory, Long> {

	List<FeeCategory> findByStatus(FeeCategoryStatus status);
}
