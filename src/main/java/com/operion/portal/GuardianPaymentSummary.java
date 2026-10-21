package com.operion.portal;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Payment;

public record GuardianPaymentSummary(Long paymentId, String receiptNumber, LocalDate paymentDate, BigDecimal amount,
		String paymentMethod, String status) {

	public static GuardianPaymentSummary from(Payment payment) {
		return new GuardianPaymentSummary(payment.getId(), payment.getReceiptNumber(), payment.getPaymentDate(), payment.getAmount(),
				payment.getPaymentMethod().name(), payment.getStatus().name());
	}
}
