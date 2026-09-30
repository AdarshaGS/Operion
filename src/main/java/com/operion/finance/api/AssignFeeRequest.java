package com.operion.finance.api;

import java.math.BigDecimal;

import com.operion.finance.PaymentFrequency;

public record AssignFeeRequest(
		Long studentEnrollmentId, Long feeStructureId, BigDecimal discountAmount, String discountReason, Long approvedBy,
		PaymentFrequency paymentFrequency, String proofFileReference, String proofFileName) {
}
