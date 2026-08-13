package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.operion.finance.FeePaymentGatewayService;

public record PaymentLinkStatusResponse(String status, String invoiceNumber, BigDecimal outstanding, Instant expiresAt) {

	public static PaymentLinkStatusResponse from(FeePaymentGatewayService.LinkStatus linkStatus) {
		return new PaymentLinkStatusResponse(
				linkStatus.status().name(), linkStatus.invoiceNumber(), linkStatus.outstanding(), linkStatus.expiresAt());
	}
}
