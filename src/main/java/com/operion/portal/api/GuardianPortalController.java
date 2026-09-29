package com.operion.portal.api;

import java.util.List;

import com.operion.common.TenantContext;
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

	private final GuardianPortalService guardianPortalService;

	public GuardianPortalController(GuardianPortalService guardianPortalService) {
		this.guardianPortalService = guardianPortalService;
	}

	@GetMapping("/children/{studentId}/attendance")
	public GuardianAttendanceSummary attendance(@PathVariable Long studentId) {
		return guardianPortalService.attendanceFor(TenantContext.getActorId(), studentId);
	}

	@GetMapping("/children/{studentId}/fees")
	public List<GuardianInvoiceSummary> fees(@PathVariable Long studentId) {
		return guardianPortalService.feesFor(TenantContext.getActorId(), studentId);
	}

	@GetMapping("/children/{studentId}/report-cards")
	public List<GuardianReportCardSummary> reportCards(@PathVariable Long studentId) {
		return guardianPortalService.reportCardsFor(TenantContext.getActorId(), studentId);
	}

	@GetMapping("/announcements")
	public List<PortalAnnouncementSummary> announcements() {
		return guardianPortalService.announcementsFor(TenantContext.getActorId());
	}
}
