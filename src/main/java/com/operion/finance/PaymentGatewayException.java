package com.operion.finance;

/** The gateway call itself failed (missing/invalid credentials, network error, gateway
 * outage) - distinct from a business-rule rejection like "already paid," which the
 * caller can fix; this one the caller can only retry or report. */
public class PaymentGatewayException extends RuntimeException {

	public PaymentGatewayException(String message, Throwable cause) {
		super(message, cause);
	}
}
