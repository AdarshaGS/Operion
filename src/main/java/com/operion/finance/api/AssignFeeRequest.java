package com.operion.finance.api;

import java.math.BigDecimal;

public record AssignFeeRequest(
		Long studentEnrollmentId, Long feeStructureId, BigDecimal discountAmount, String discountReason, Long approvedBy) {
}
