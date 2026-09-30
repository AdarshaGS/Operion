package com.operion.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryStructureLineRepository extends JpaRepository<SalaryStructureLine, Long> {

	List<SalaryStructureLine> findBySalaryStructureId(Long salaryStructureId);
}
