package com.operion.payroll.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.operion.payroll.PayrollRun;
import com.operion.payroll.Payslip;

/** Totals are split teacher vs. other staff; payslips is empty on the runs list. */
public record PayrollRunResponse(Long id, int payYear, int payMonth, String status, Long supersedesRunId,
		Instant finalizedAt, int payslipCount, Totals teacherTotals, Totals staffTotals, List<PayslipResponse> payslips) {

	public record Totals(int count, BigDecimal gross, BigDecimal deductions, BigDecimal net) {
	}

	static PayrollRunResponse from(PayrollRun run, List<Payslip> payslips, boolean includePayslips) {
		return new PayrollRunResponse(run.getId(), run.getPayYear(), run.getPayMonth(), run.getStatus().name(),
				run.getSupersedes() == null ? null : run.getSupersedes().getId(), run.getFinalizedAt(), payslips.size(),
				totals(payslips, true), totals(payslips, false),
				includePayslips ? payslips.stream().map(slip -> PayslipResponse.from(slip, List.of())).toList() : List.of());
	}

	private static Totals totals(List<Payslip> payslips, boolean teacher) {
		List<Payslip> matching = payslips.stream().filter(slip -> slip.isTeacher() == teacher).toList();
		return new Totals(matching.size(), sum(matching.stream().map(Payslip::getGrossEarnings)),
				sum(matching.stream().map(Payslip::getTotalDeductions)), sum(matching.stream().map(Payslip::getNetPay)));
	}

	private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
		return values.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
