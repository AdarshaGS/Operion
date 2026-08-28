package com.operion.sales;

/**
 * Sales-scoped rather than reusing finance.PaymentMethod - that enum's ONLINE value is
 * gateway-only (see RazorpayGateway), which doesn't apply to an in-person store sale, and
 * Sale is deliberately kept independent of the finance module (GitHub #60/#62).
 */
public enum PaymentMethod {
	CASH,
	CARD,
	UPI,
	CHEQUE,
	BANK_TRANSFER
}
