package com.operion.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.sales.SalePayment;

public record SalePaymentResponse(Long id, String paymentMethod, BigDecimal amount, LocalDate paidAt) {

	public static SalePaymentResponse from(SalePayment payment) {
		return new SalePaymentResponse(payment.getId(), payment.getPaymentMethod().name(), payment.getAmount(), payment.getPaidAt());
	}
}
