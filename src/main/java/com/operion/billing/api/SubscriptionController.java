package com.operion.billing.api;

import java.util.List;

import com.operion.billing.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/organisations/{organisationId}/subscriptions")
public class SubscriptionController {

	private final BillingService billingService;

	public SubscriptionController(BillingService billingService) {
		this.billingService = billingService;
	}

	@PostMapping
	public SubscriptionResponse subscribe(@PathVariable Long organisationId, @RequestBody CreateSubscriptionRequest request) {
		return SubscriptionResponse.from(billingService.subscribe(organisationId, request.planId(), request.startDate()));
	}

	@GetMapping
	public List<SubscriptionResponse> history(@PathVariable Long organisationId) {
		return billingService.subscriptionHistory(organisationId).stream().map(SubscriptionResponse::from).toList();
	}
}
