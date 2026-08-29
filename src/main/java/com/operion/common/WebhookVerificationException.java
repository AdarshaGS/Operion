package com.operion.common;

/** A public, unauthenticated webhook endpoint's signature/secret check failed - see
 * FeePaymentGatewayService.handleWebhook (Razorpay) and NotificationDeliveryWebhookService
 * (email/SMS provider delivery callbacks) for the two callers. */
public class WebhookVerificationException extends RuntimeException {

	public WebhookVerificationException(String message) {
		super(message);
	}
}
