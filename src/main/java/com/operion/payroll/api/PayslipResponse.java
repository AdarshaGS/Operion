package com.operion.payroll.api;

import java.math.BigDecimal;
import java.util.List;

import com.operion.payroll.ComponentType;
import com.operion.payroll.Payslip;
import com.operion.payroll.PayslipLine;
import com.operion.payroll.PayslipLineSource;

/** lines is empty on list views and populated on the single-payslip view. */
public record PayslipResponse(Long id, Long payrollRunId, int payYear, int payMonth, String runStatus,
		Long staffProfileId, String employeeCode, String staffName, String designationName, String departmentName,
		boolean teacher, BigDecimal basicAmount, BigDecimal grossEarnings, BigDecimal totalDeductions,
		BigDecimal netPay, List<LineResponse> lines) {

	public record LineResponse(Long id, String label, ComponentType type, PayslipLineSource source, BigDecimal amount) {
	}

	public static PayslipResponse from(Payslip slip, List<PayslipLine> lines) {
		return new PayslipResponse(slip.getId(), slip.getPayrollRun().getId(), slip.getPayrollRun().getPayYear(),
				slip.getPayrollRun().getPayMonth(), slip.getPayrollRun().getStatus().name(),
				slip.getStaffProfile().getId(), slip.getEmployeeCode(), slip.getStaffName(), slip.getDesignationName(),
				slip.getDepartmentName(), slip.isTeacher(), slip.getBasicAmount(), slip.getGrossEarnings(),
				slip.getTotalDeductions(), slip.getNetPay(),
				lines.stream().map(line -> new LineResponse(line.getId(), line.getLabel(), line.getType(),
						line.getSource(), line.getAmount())).toList());
	}
}
