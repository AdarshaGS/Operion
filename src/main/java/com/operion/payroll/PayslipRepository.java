package com.operion.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

	List<Payslip> findByPayrollRunIdOrderByStaffNameAsc(Long payrollRunId);

	/** A staff member's payslips from finalized runs only, newest month first. */
	List<Payslip> findByStaffProfileIdAndPayrollRunStatusOrderByPayrollRunPayYearDescPayrollRunPayMonthDesc(
			Long staffProfileId, PayrollRunStatus status);
}
