package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureInstallmentRepository extends JpaRepository<FeeStructureInstallment, Long> {

	List<FeeStructureInstallment> findByFeeStructureIdOrderByInstallmentNumber(Long feeStructureId);
}
