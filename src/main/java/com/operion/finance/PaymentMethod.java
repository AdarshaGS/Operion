package com.operion.finance;

public enum PaymentMethod {
	CASH,
	CHEQUE,
	UPI,
	CARD,
	BANK_TRANSFER,
	/** Collected through a gateway payment link, not entered by staff - see PaymentGatewayOrder. */
	ONLINE
}
