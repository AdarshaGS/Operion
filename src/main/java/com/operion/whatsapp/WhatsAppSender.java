package com.operion.whatsapp;

/**
 * Seam between {@link WhatsAppDeliveryService} and a real provider's HTTP API - same
 * "hand-written stub over a mock HTTP server" testability pattern as
 * com.operion.sms.SmsSender.
 */
public interface WhatsAppSender {

	/** @return the provider's own message id for this send, so a later delivery webhook
	 * can be correlated back to it.
	 * @throws WhatsAppSendException if not configured, or the provider rejects/fails the send. */
	String send(WhatsAppMessage message);

	/** Short, stable identifier recorded on the dispatching NotificationRecipient row when this sender succeeds. */
	String providerName();
}
