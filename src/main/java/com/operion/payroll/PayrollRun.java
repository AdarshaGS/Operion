package com.operion.payroll;

import java.time.Instant;
import java.time.YearMonth;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One month's payroll. DRAFT -> FINALIZED (locked) -> SUPERSEDED (only when a correction run
 * for the same month is finalized). A correction is a new run pointing back at the one it
 * replaces - finalized payslips are never edited, same lock-then-explicit-correction shape as
 * published report cards.
 */
@Getter
@Entity
@Table(name = "payroll_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRun extends TenantScopedEntity {

	@Column(name = "pay_year", nullable = false)
	private int payYear;

	@Column(name = "pay_month", nullable = false)
	private int payMonth;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PayrollRunStatus status;

	/** Set on a correction run: the finalized run it will replace once finalized itself. */
	@ManyToOne
	@JoinColumn(name = "supersedes_run_id")
	private PayrollRun supersedes;

	@Column(name = "finalized_at")
	private Instant finalizedAt;

	public PayrollRun(YearMonth period, PayrollRun supersedes) {
		this.payYear = period.getYear();
		this.payMonth = period.getMonthValue();
		this.supersedes = supersedes;
		this.status = PayrollRunStatus.DRAFT;
	}

	public YearMonth period() {
		return YearMonth.of(payYear, payMonth);
	}

	public void finalizeRun() {
		if (status != PayrollRunStatus.DRAFT) {
			throw new IllegalStateException("Only a draft payroll run can be finalized");
		}
		this.status = PayrollRunStatus.FINALIZED;
		this.finalizedAt = Instant.now();
	}

	public void markSuperseded() {
		if (status != PayrollRunStatus.FINALIZED) {
			throw new IllegalStateException("Only a finalized payroll run can be superseded");
		}
		this.status = PayrollRunStatus.SUPERSEDED;
	}
}
