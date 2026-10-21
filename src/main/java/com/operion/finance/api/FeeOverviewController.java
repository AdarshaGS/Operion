package com.operion.finance.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.authorization.RequirePermission;
import com.operion.finance.Invoice;
import com.operion.finance.InvoiceRepository;
import com.operion.student.StudentEnrollment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only rollup behind the Fees & Collections page. Aggregates in memory off the invoice
 * rows - one org's invoices per academic year stay in the low thousands, and every figure
 * must agree with the per-invoice outstanding balance the collection screens already show. */
@RestController
@RequestMapping("/api/v1/fees/overview")
@RequirePermission("FEE_VIEW")
public class FeeOverviewController {

	private static final int TREND_MONTHS = 6;

	private final InvoiceRepository invoiceRepository;

	public FeeOverviewController(InvoiceRepository invoiceRepository) {
		this.invoiceRepository = invoiceRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public FeeOverviewResponse overview(@RequestParam(required = false) Long academicYearId) {
		LocalDate today = LocalDate.now();
		List<Invoice> invoices = invoiceRepository.findForOverview(academicYearId);

		BigDecimal total = BigDecimal.ZERO;
		BigDecimal collected = BigDecimal.ZERO;
		BigDecimal overdue = BigDecimal.ZERO;
		Map<Long, List<Invoice>> byEnrollment = new LinkedHashMap<>();
		Map<String, BigDecimal> byType = new LinkedHashMap<>();
		Map<Long, List<Invoice>> byClass = new LinkedHashMap<>();
		for (Invoice invoice : invoices) {
			total = total.add(invoice.getTotalAmount());
			collected = collected.add(invoice.getAmountPaid());
			if (isOverdue(invoice, today)) {
				overdue = overdue.add(invoice.getOutstanding());
			}
			StudentEnrollment enrollment = invoice.getStudentFeeAssignment().getStudentEnrollment();
			byEnrollment.computeIfAbsent(enrollment.getId(), key -> new ArrayList<>()).add(invoice);
			byClass.computeIfAbsent(enrollment.getSection().getSchoolClass().getId(), key -> new ArrayList<>()).add(invoice);
			String typeName = invoice.getStudentFeeAssignment().getFeeStructure().getFeeCategory().getName();
			byType.merge(typeName, invoice.getTotalAmount(), BigDecimal::add);
		}

		List<FeeOverviewResponse.LedgerRow> ledger = byEnrollment.values().stream().map(rows -> ledgerRow(rows, today)).toList();
		List<FeeOverviewResponse.ClassCollection> classRows = byClass.values().stream().map(this::classCollection)
				.sorted(Comparator.comparing(FeeOverviewResponse.ClassCollection::classLabel)).toList();
		List<FeeOverviewResponse.TypeShare> typeRows = byType.entrySet().stream()
				.map(entry -> new FeeOverviewResponse.TypeShare(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparing(FeeOverviewResponse.TypeShare::amount).reversed()).toList();

		return new FeeOverviewResponse(total, collected, total.subtract(collected), overdue, ledger, trend(invoices, today), classRows, typeRows);
	}

	private static boolean isOverdue(Invoice invoice, LocalDate today) {
		return invoice.getOutstanding().signum() > 0 && invoice.getDueDate().isBefore(today);
	}

	private static String classLabel(StudentEnrollment enrollment) {
		return enrollment.getSection().getSchoolClass().label() + " - " + enrollment.getSection().getName();
	}

	private FeeOverviewResponse.LedgerRow ledgerRow(List<Invoice> rows, LocalDate today) {
		StudentEnrollment enrollment = rows.get(0).getStudentFeeAssignment().getStudentEnrollment();
		BigDecimal total = rows.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal paid = rows.stream().map(Invoice::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal outstanding = total.subtract(paid);
		LocalDate nextDue = rows.stream().filter(i -> i.getOutstanding().signum() > 0).map(Invoice::getDueDate).min(Comparator.naturalOrder()).orElse(null);
		String status;
		if (outstanding.signum() <= 0) {
			status = "PAID";
		} else if (rows.stream().anyMatch(i -> isOverdue(i, today))) {
			status = "OVERDUE";
		} else {
			status = paid.signum() > 0 ? "PARTIAL" : "UNPAID";
		}
		var person = enrollment.getStudent().getPerson();
		return new FeeOverviewResponse.LedgerRow(enrollment.getId(), enrollment.getAcademicYear().getId(), enrollment.getStudent().getId(), person.getFirstName() + " " + person.getLastName(),
				enrollment.getStudent().getAdmissionNumber(), enrollment.getSection().getSchoolClass().getId(), classLabel(enrollment), total, paid,
				outstanding, nextDue, status);
	}

	private FeeOverviewResponse.ClassCollection classCollection(List<Invoice> rows) {
		StudentEnrollment enrollment = rows.get(0).getStudentFeeAssignment().getStudentEnrollment();
		BigDecimal total = rows.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal paid = rows.stream().map(Invoice::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
		int percent = total.signum() == 0 ? 0 : paid.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
		var schoolClass = enrollment.getSection().getSchoolClass();
		return new FeeOverviewResponse.ClassCollection(schoolClass.getId(), schoolClass.label(), total, paid, percent);
	}

	/** The last TREND_MONTHS months up to and including the current one, zero-filled so the chart axis has no gaps. */
	private List<FeeOverviewResponse.MonthPoint> trend(List<Invoice> invoices, LocalDate today) {
		YearMonth current = YearMonth.from(today);
		List<FeeOverviewResponse.MonthPoint> points = new ArrayList<>();
		for (int back = TREND_MONTHS - 1; back >= 0; back--) {
			YearMonth month = current.minusMonths(back);
			BigDecimal paid = BigDecimal.ZERO;
			BigDecimal owed = BigDecimal.ZERO;
			for (Invoice invoice : invoices) {
				if (YearMonth.from(invoice.getDueDate()).equals(month)) {
					paid = paid.add(invoice.getAmountPaid());
					owed = owed.add(invoice.getOutstanding());
				}
			}
			points.add(new FeeOverviewResponse.MonthPoint(month.toString(), paid, owed));
		}
		return points;
	}
}
