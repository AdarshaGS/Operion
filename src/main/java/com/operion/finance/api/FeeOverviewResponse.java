package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Fees page's org-wide rollup - headline totals, one ledger row per student enrollment,
 * and the three chart series. Everything is computed from invoices, so a student with no
 * invoice yet simply has no row. */
public record FeeOverviewResponse(BigDecimal totalFees, BigDecimal collected, BigDecimal outstanding, BigDecimal overdue,
		List<LedgerRow> ledger, List<MonthPoint> trend, List<ClassCollection> byClass, List<TypeShare> byType) {

	/** status is PAID, PARTIAL, UNPAID or OVERDUE; nextDueDate is null once fully paid. */
	public record LedgerRow(Long studentEnrollmentId, Long academicYearId, Long studentId, String studentName, String admissionNumber, Long schoolClassId,
			String classLabel, BigDecimal totalFees, BigDecimal paid, BigDecimal outstanding, LocalDate nextDueDate, String status) {
	}

	/** month is yyyy-MM, attributed by invoice due date (invoices carry no issue date). */
	public record MonthPoint(String month, BigDecimal collected, BigDecimal outstanding) {
	}

	public record ClassCollection(Long schoolClassId, String classLabel, BigDecimal total, BigDecimal collected, int percent) {
	}

	public record TypeShare(String name, BigDecimal amount) {
	}
}
