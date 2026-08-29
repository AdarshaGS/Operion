package com.operion.email;

/**
 * Seam between {@link EmailDeliveryService} and a real provider's HTTP API - same
 * "hand-written stub over a mock HTTP server" testability pattern as
 * com.operion.finance.RazorpayGateway.
 */
public interface EmailSender {

	/** @return the provider's own message id for this send, so a later delivery webhook
	 * (see NotificationDeliveryWebhookService) can be correlated back to it.
	 * @throws EmailSendException if not configured, or the provider rejects/fails the send. */
	String send(EmailMessage message);

	/** Short, stable identifier recorded on the EmailOutbox row when this sender succeeds. */
	String providerName();
}
