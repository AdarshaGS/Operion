package com.operion.platform.crm.api;

import java.util.List;

import com.operion.platform.crm.CrmDirectory;
import com.operion.platform.crm.CrmMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pipeline metrics for the platform dashboard, plus the platform-admin list the console
 * needs for its owner/assignee pickers (there's no other endpoint that lists platform admins). */
@RestController
@RequestMapping("/api/v1/platform/crm")
public class CrmMetricsController {

	private final CrmMetricsService crmMetricsService;
	private final CrmDirectory crmDirectory;

	public CrmMetricsController(CrmMetricsService crmMetricsService, CrmDirectory crmDirectory) {
		this.crmMetricsService = crmMetricsService;
		this.crmDirectory = crmDirectory;
	}

	@GetMapping("/metrics")
	public CrmMetricsResponse metrics() {
		return CrmMetricsResponse.from(crmMetricsService.current());
	}

	@GetMapping("/admins")
	public List<CrmAdminResponse> admins() {
		return crmDirectory.admins().stream().map(CrmAdminResponse::from).toList();
	}
}
