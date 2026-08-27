package com.operion.email;

/**
 * Seam between {@link EmailDeliveryService} and a real provider's HTTP API - same
 * "hand-written stub over a mock HTTP server" testability pattern as
 * com.operion.finance.RazorpayGateway.
 */
public interface EmailSender {

	/** @throws EmailSendException if not configured, or the provider rejects/fails the send. */
	void send(EmailMessage message);

	/** Short, stable identifier recorded on the EmailOutbox row when this sender succeeds. */
	String providerName();
}
