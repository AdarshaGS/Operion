package com.operion.email;

/** Thrown by an {@link EmailSender} on any failure - missing config, an HTTP error, an
 * unreachable provider. Caught inside {@link EmailDeliveryService} to try the next
 * configured sender rather than propagating, so a single provider outage never blocks
 * the business action the email was attached to. */
public class EmailSendException extends RuntimeException {

	public EmailSendException(String message) {
		super(message);
	}

	public EmailSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
