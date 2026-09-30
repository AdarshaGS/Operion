package com.operion.payroll;

import java.math.BigDecimal;

import com.operion.common.TenantScopedEntity;
import com.operion.hr.StaffProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One staff member's pay for one run. Identity fields are snapshotted at generation so the
 * payslip stays accurate after a rename, transfer or designation change. Totals are stored
 * (recomputed from the lines on every draft change) rather than derived on read, so a
 * finalized payslip is a fixed record. Lines live in PayslipLine (FK only).
 */
@Getter
@Entity
@Table(name = "payslips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payslip extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "payroll_run_id")
	private PayrollRun payrollRun;

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@Column(name = "employee_code", nullable = false)
	private String employeeCode;

	@Column(name = "staff_name", nullable = false)
	private String staffName;

	@Column(name = "designation_name")
	private String designationName;

	@Column(name = "department_name")
	private String departmentName;

	@Column(name = "is_teacher", nullable = false)
	private boolean teacher;

	@Column(name = "basic_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal basicAmount;

	@Column(name = "gross_earnings", nullable = false, precision = 14, scale = 2)
	private BigDecimal grossEarnings;

	@Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalDeductions;

	@Column(name = "net_pay", nullable = false, precision = 14, scale = 2)
	private BigDecimal netPay;

	public Payslip(PayrollRun payrollRun, StaffProfile staffProfile, String staffName, boolean teacher,
			BigDecimal basicAmount) {
		this.payrollRun = payrollRun;
		this.staffProfile = staffProfile;
		this.employeeCode = staffProfile.getEmployeeCode();
		this.staffName = staffName;
		this.designationName = staffProfile.getDesignation().getName();
		this.departmentName = staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getName();
		this.teacher = teacher;
		this.basicAmount = basicAmount;
		applyTotals(basicAmount, BigDecimal.ZERO);
	}

	/** gross includes basic; net = gross - deductions. */
	public void applyTotals(BigDecimal grossEarnings, BigDecimal totalDeductions) {
		this.grossEarnings = grossEarnings;
		this.totalDeductions = totalDeductions;
		this.netPay = grossEarnings.subtract(totalDeductions);
	}
}
