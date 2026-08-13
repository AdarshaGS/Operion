package com.operion.billing.api;

import java.util.List;

import com.operion.billing.BillingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformInvoiceController {

	private final BillingService billingService;

	public PlatformInvoiceController(BillingService billingService) {
		this.billingService = billingService;
	}

	@PostMapping("/api/v1/platform/organisations/{organisationId}/invoices/generate")
	public PlatformInvoiceResponse generate(@PathVariable Long organisationId, @RequestBody GenerateInvoiceRequest request) {
		return PlatformInvoiceResponse.from(
				billingService.generateInvoice(organisationId, request.periodStart(), request.periodEnd(), request.dueDate()));
	}

	@GetMapping("/api/v1/platform/organisations/{organisationId}/invoices")
	public List<PlatformInvoiceResponse> history(@PathVariable Long organisationId) {
		return billingService.invoiceHistory(organisationId).stream().map(PlatformInvoiceResponse::from).toList();
	}

	@PostMapping("/api/v1/platform/invoices/{id}/mark-paid")
	public PlatformInvoiceResponse markPaid(@PathVariable Long id) {
		return PlatformInvoiceResponse.from(billingService.markPaid(id));
	}

	/** Cross-org, for the platform dashboard - see BillingService.allInvoices(). */
	@GetMapping("/api/v1/platform/invoices")
	public List<PlatformInvoiceResponse> all() {
		return billingService.allInvoices().stream().map(PlatformInvoiceResponse::from).toList();
	}
}
