package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

import com.operion.finance.PaymentFrequency;

public record CreateFeeStructureRequest(Long feeStructureGroupId, Long feeCategoryId, BigDecimal amount,
		List<InstallmentEntry> installments, PaymentFrequency paymentFrequency, BigDecimal oneShotDiscountAmount) {
}
