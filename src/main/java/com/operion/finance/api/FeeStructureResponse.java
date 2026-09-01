package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureInstallment;

public record FeeStructureResponse(Long id, Long feeStructureGroupId, Long feeCategoryId,
		BigDecimal amount, String status, List<FeeStructureInstallmentResponse> installments) {

	static FeeStructureResponse from(FeeStructure structure, List<FeeStructureInstallment> installments) {
		return new FeeStructureResponse(structure.getId(), structure.getFeeStructureGroup().getId(),
				structure.getFeeCategory().getId(), structure.getAmount(), structure.getStatus().name(),
				installments.stream().map(FeeStructureInstallmentResponse::from).toList());
	}
}
