package com.operion.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipLineRepository extends JpaRepository<PayslipLine, Long> {

	List<PayslipLine> findByPayslipIdOrderByIdAsc(Long payslipId);
}
