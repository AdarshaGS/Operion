package com.operion.finance.api;

import java.time.Instant;

import com.operion.finance.FeePaymentGatewayService;

public record PaymentLinkResponse(Long orderId, String organisationSlug, String linkToken, Instant expiresAt) {

	public static PaymentLinkResponse from(FeePaymentGatewayService.IssuedLink issued) {
		return new PaymentLinkResponse(issued.orderId(), issued.organisationSlug(), issued.linkToken(), issued.expiresAt());
	}
}
