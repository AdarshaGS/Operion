package com.operion.finance.api;

import java.math.BigDecimal;

import com.operion.finance.PaymentFrequency;

public record ReviseAssignmentRequest(BigDecimal discountAmount, String discountReason, Long approvedBy,
		PaymentFrequency paymentFrequency, String proofFileReference, String proofFileName) {
}
