package com.operion.finance.api;

import com.operion.finance.FeePaymentGatewayService;

public record CheckoutSessionResponse(String gatewayOrderId, String razorpayKeyId, long amountInPaise, String currency, String invoiceNumber) {

	public static CheckoutSessionResponse from(FeePaymentGatewayService.CheckoutSession session) {
		return new CheckoutSessionResponse(
				session.gatewayOrderId(), session.razorpayKeyId(), session.amountInPaise(), session.currency(), session.invoiceNumber());
	}
}
