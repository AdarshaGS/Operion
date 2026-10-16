package com.operion.dashboard.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Time-series and breakdown data behind the dashboard charts - kept off the summary
 * endpoint so the headline tiles stay a single light call.
 */
public record DashboardChartsResponse(List<AttendanceTrendPoint> attendanceTrend, List<FeeCollectionPoint> feeCollections,
		List<ClassEnrollmentPoint> studentsByClass) {

	/** One school day: present-rate uses the same formula as the "Today's attendance" tile (present / marked). */
	public record AttendanceTrendPoint(LocalDate date, long marked, long present, int ratePercent) {
	}

	/** One calendar month ("2026-09"): cleared payments received vs. invoices falling due. */
	public record FeeCollectionPoint(String month, BigDecimal collected, BigDecimal invoiced) {
	}

	public record ClassEnrollmentPoint(String className, long students) {
	}
}
