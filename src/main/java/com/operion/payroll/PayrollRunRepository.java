package com.operion.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

	List<PayrollRun> findAllByOrderByPayYearDescPayMonthDescIdDesc();

	List<PayrollRun> findByPayYearAndPayMonthAndStatusIn(int payYear, int payMonth, List<PayrollRunStatus> statuses);
}
