package com.operion.billing.api;

import java.util.List;

import com.operion.billing.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionController {

	private final BillingService billingService;

	public SubscriptionController(BillingService billingService) {
		this.billingService = billingService;
	}

	@PostMapping("/api/v1/platform/organisations/{organisationId}/subscriptions")
	public SubscriptionResponse subscribe(@PathVariable Long organisationId, @RequestBody CreateSubscriptionRequest request) {
		return SubscriptionResponse.from(billingService.subscribe(organisationId, request.planId(), request.startDate()));
	}

	@GetMapping("/api/v1/platform/organisations/{organisationId}/subscriptions")
	public List<SubscriptionResponse> history(@PathVariable Long organisationId) {
		return billingService.subscriptionHistory(organisationId).stream().map(SubscriptionResponse::from).toList();
	}

	/** Cross-org, for the platform dashboard - see BillingService.allSubscriptions(). */
	@GetMapping("/api/v1/platform/subscriptions")
	public List<SubscriptionResponse> all() {
		return billingService.allSubscriptions().stream().map(SubscriptionResponse::from).toList();
	}
}
