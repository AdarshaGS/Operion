package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record CreateFeeStructureRequest(
		Long feeStructureGroupId, Long feeCategoryId, BigDecimal amount, List<InstallmentEntry> installments) {
}
