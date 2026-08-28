package com.operion.dashboard.api;

public record DashboardSummaryResponse(EnrollmentSummary enrollment, AttendanceSummary attendanceToday, FeeSummary fees,
		StaffSummary staff, ExaminationSummary examinations, LibrarySummary library, TransportSummary transport,
		InventorySummary inventory, CommunicationSummary communication, SalesSummary sales, PurchaseSummary purchase,
		SetupChecklist setupChecklist) {
}
