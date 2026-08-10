package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

import com.operion.finance.FeeStructure;
import com.operion.finance.FeeStructureInstallment;

public record FeeStructureResponse(Long id, Long academicYearId, Long schoolClassId, Long feeCategoryId,
		BigDecimal amount, String status, List<FeeStructureInstallmentResponse> installments) {

	static FeeStructureResponse from(FeeStructure structure, List<FeeStructureInstallment> installments) {
		return new FeeStructureResponse(structure.getId(), structure.getAcademicYear().getId(), structure.getSchoolClass().getId(),
				structure.getFeeCategory().getId(), structure.getAmount(), structure.getStatus().name(),
				installments.stream().map(FeeStructureInstallmentResponse::from).toList());
	}
}
