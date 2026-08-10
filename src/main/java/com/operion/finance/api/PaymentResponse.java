package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Payment;

public record PaymentResponse(Long id, Long academicYearId, String receiptNumber, BigDecimal amount,
		String paymentMethod, LocalDate paymentDate, String status, String remarks) {

	static PaymentResponse from(Payment payment) {
		return new PaymentResponse(payment.getId(), payment.getAcademicYear().getId(), payment.getReceiptNumber(),
				payment.getAmount(), payment.getPaymentMethod().name(), payment.getPaymentDate(), payment.getStatus().name(),
				payment.getRemarks());
	}
}
