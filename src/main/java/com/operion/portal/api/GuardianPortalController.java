package com.operion.portal.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.modules.AppModule;
import com.operion.modules.RequireModule;
import com.operion.document.pdf.PdfResponses;
import com.operion.examination.ReportCardPdfService;
import com.operion.finance.ReceiptService;
import com.operion.portal.GuardianPaymentService;
import com.operion.portal.GuardianPaymentSummary;
import org.springframework.http.ResponseEntity;
import com.operion.portal.GuardianAttendanceSummary;
import com.operion.portal.GuardianInvoiceSummary;
import com.operion.portal.GuardianPortalService;
import com.operion.portal.GuardianReportCardSummary;
import com.operion.portal.PortalAnnouncementSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @RequirePermission} - same convention as MeController. Access is scoped by
 * data, not by permission code: every method verifies the caller is actually linked to
 * the requested student before returning anything (see GuardianPortalService).
 */
@RestController
@RequestMapping("/api/v1/me")
public class GuardianPortalController {

	private final GuardianPaymentService guardianPaymentService;
	private final ReceiptService receiptService;
	private final GuardianPortalService guardianPortalService;
	private final ReportCardPdfService reportCardPdfService;

	public GuardianPortalController(GuardianPortalService guardianPortalService, GuardianPaymentService guardianPaymentService,
			ReceiptService receiptService, ReportCardPdfService reportCardPdfService) {
		this.reportCardPdfService = reportCardPdfService;
		this.guardianPaymentService = guardianPaymentService;
		this.receiptService = receiptService;
		this.guardianPortalService = guardianPortalService;
	}

	@GetMapping("/children/{studentId}/attendance")
	@RequireModule(AppModule.ATTENDANCE)
	public GuardianAttendanceSummary attendance(@PathVariable Long studentId) {
		return guardianPortalService.attendanceFor(TenantContext.getActorId(), studentId);
	}

	@GetMapping("/children/{studentId}/fees")
	@RequireModule(AppModule.FEES)
	public List<GuardianInvoiceSummary> fees(@PathVariable Long studentId) {
		return guardianPortalService.feesFor(TenantContext.getActorId(), studentId);
	}

	@GetMapping("/children/{studentId}/payments")
	@RequireModule(AppModule.FEES)
	public List<GuardianPaymentSummary> payments(@PathVariable Long studentId) {
		return guardianPaymentService.paymentsFor(TenantContext.getActorId(), studentId);
	}

	/** The guardian's own copy - never counted as an office print, so it never says DUPLICATE. */
	@GetMapping("/children/{studentId}/payments/{paymentId}/receipt.pdf")
	@RequireModule(AppModule.FEES)
	public ResponseEntity<byte[]> receipt(@PathVariable Long studentId, @PathVariable Long paymentId) {
		guardianPaymentService.requireReceiptAccess(TenantContext.getActorId(), studentId, paymentId);
		ReceiptService.PreparedReceipt receipt = receiptService.prepare(paymentId, false);
		return PdfResponses.attachment(receiptService.renderPdf(receipt), "receipt-" + receipt.snapshot().receiptNumber());
	}

	@GetMapping("/children/{studentId}/report-cards")
	@RequireModule(AppModule.EXAMINATIONS)
	public List<GuardianReportCardSummary> reportCards(@PathVariable Long studentId) {
		return guardianPortalService.reportCardsFor(TenantContext.getActorId(), studentId);
	}

	/** The guardian's own copy of a published, current report card - never counted as an office print. */
	@GetMapping("/children/{studentId}/report-cards/{reportCardId}/report-card.pdf")
	@RequireModule(AppModule.EXAMINATIONS)
	public ResponseEntity<byte[]> reportCardPdf(@PathVariable Long studentId, @PathVariable Long reportCardId) {
		guardianPortalService.requireReportCardAccess(TenantContext.getActorId(), studentId, reportCardId);
		ReportCardPdfService.PreparedReportCards prepared = reportCardPdfService.prepare(reportCardId, false);
		return PdfResponses.attachment(reportCardPdfService.renderPdf(prepared), prepared.fileName());
	}

	@GetMapping("/announcements")
	@RequireModule(AppModule.COMMUNICATION)
	public List<PortalAnnouncementSummary> announcements() {
		return guardianPortalService.announcementsFor(TenantContext.getActorId());
	}
}
