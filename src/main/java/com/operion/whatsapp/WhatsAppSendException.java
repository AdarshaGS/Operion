package com.operion.whatsapp;

/** Thrown by a {@link WhatsAppSender} on any failure - missing config, an HTTP error, an
 * unreachable provider. Same role as com.operion.sms.SmsSendException. */
public class WhatsAppSendException extends RuntimeException {

	public WhatsAppSendException(String message) {
		super(message);
	}

	public WhatsAppSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
