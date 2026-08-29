package com.operion.sms;

/**
 * Seam between {@link SmsDeliveryService} and a real provider's HTTP API - same
 * "hand-written stub over a mock HTTP server" testability pattern as
 * com.operion.email.EmailSender.
 */
public interface SmsSender {

	/** @return the provider's own message id for this send, so a later delivery webhook
	 * can be correlated back to it.
	 * @throws SmsSendException if not configured, or the provider rejects/fails the send. */
	String send(SmsMessage message);

	/** Short, stable identifier recorded on the dispatching NotificationRecipient row when this sender succeeds. */
	String providerName();
}
