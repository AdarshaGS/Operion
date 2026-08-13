package com.operion.finance;

/**
 * Seam between FeePaymentGatewayService and the real Razorpay HTTP API - lets tests
 * substitute a hand-written stub (this codebase's established pattern for external
 * dependencies, e.g. AuditLogService in @DataJpaTest slices) instead of hitting a real
 * gateway or standing up a mock HTTP server.
 */
public interface RazorpayGateway {

	/** Returns the gateway's own order id. */
	String createOrder(RazorpayCredentials credentials, long amountInPaise, String currency, String receipt);

	boolean verifyWebhookSignature(String rawBody, String signatureHeader, String webhookSecret);
}
