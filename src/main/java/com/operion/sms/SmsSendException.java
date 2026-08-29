package com.operion.sms;

/** Thrown by an {@link SmsSender} on any failure - missing config, an HTTP error, an
 * unreachable provider. Same role as com.operion.email.EmailSendException. */
public class SmsSendException extends RuntimeException {

	public SmsSendException(String message) {
		super(message);
	}

	public SmsSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
