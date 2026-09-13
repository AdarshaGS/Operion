package com.operion.billing.api;

import java.util.List;

import com.operion.billing.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operations > Usage - see BillingService.usageByOrganisation() for what "usage" means
 * here (active student headcount) and why. */
@RestController
public class PlatformUsageController {

	private final BillingService billingService;

	public PlatformUsageController(BillingService billingService) {
		this.billingService = billingService;
	}

	@GetMapping("/api/v1/platform/usage")
	public List<UsageResponse> usage() {
		return billingService.usageByOrganisation().stream().map(UsageResponse::from).toList();
	}
}
