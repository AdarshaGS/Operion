package com.operion.finance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeCategoryRepository extends JpaRepository<FeeCategory, Long> {

	List<FeeCategory> findByStatus(FeeCategoryStatus status);

	Optional<FeeCategory> findByNameIgnoreCase(String name);

	Optional<FeeCategory> findByCodeIgnoreCase(String code);
}
