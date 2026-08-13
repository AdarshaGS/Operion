package com.operion.finance.api;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeePaymentGatewayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-initiated "pay this invoice" links - the public, unauthenticated pieces
 * (get status, checkout) live under a class-level @RequirePermission-free mapping
 * exception below, since a parent opening the link has no token at all.
 */
@RestController
@RequestMapping("/api/v1/fees")
public class PaymentLinkController {

	private final FeePaymentGatewayService gatewayService;

	public PaymentLinkController(FeePaymentGatewayService gatewayService) {
		this.gatewayService = gatewayService;
	}

	@PostMapping("/invoices/{invoiceId}/payment-link")
	@RequirePermission("FEE_COLLECT")
	public PaymentLinkResponse createLink(@PathVariable Long invoiceId) {
		return PaymentLinkResponse.from(gatewayService.createLink(invoiceId));
	}

	// Public - registered in JwtAuthenticationInterceptor's allowlist, since a parent
	// opening this link has no bearer token at all. The org slug travels in the path
	// (embedded in the link staff shares), same shape as claim-invite carrying it in the
	// request body - there's no token yet either way to resolve it from.
	@GetMapping("/payment-links/{organisationSlug}/{linkToken}")
	public PaymentLinkStatusResponse status(@PathVariable String organisationSlug, @PathVariable String linkToken) {
		return PaymentLinkStatusResponse.from(gatewayService.getLinkStatus(organisationSlug, linkToken));
	}

	@PostMapping("/payment-links/{organisationSlug}/{linkToken}/checkout")
	public CheckoutSessionResponse checkout(@PathVariable String organisationSlug, @PathVariable String linkToken) {
		return CheckoutSessionResponse.from(gatewayService.initiateCheckout(organisationSlug, linkToken));
	}
}
