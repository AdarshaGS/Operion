package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Refund;

public record RefundResponse(Long id, Long paymentId, Long invoiceId, BigDecimal amount, String reason, Long approvedBy, LocalDate refundDate) {

	static RefundResponse from(Refund refund) {
		return new RefundResponse(refund.getId(), refund.getPayment().getId(), refund.getInvoice().getId(),
				refund.getAmount(), refund.getReason(), refund.getApprovedBy(), refund.getRefundDate());
	}
}
