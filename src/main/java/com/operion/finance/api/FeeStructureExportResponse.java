package com.operion.finance.api;

import java.math.BigDecimal;

import com.operion.finance.FeeStructure;

public record FeeStructureExportResponse(long id, String academicYear, String className, String feeCategory, BigDecimal amount,
		String paymentFrequency, String status) {

	public static FeeStructureExportResponse from(FeeStructure structure) {
		var group = structure.getFeeStructureGroup();
		var schoolClass = group.getSchoolClass();
		String className = schoolClass.getDisplayName() != null ? schoolClass.getDisplayName() : schoolClass.getGradeLevel().getName();
		return new FeeStructureExportResponse(structure.getId(), group.getAcademicYear().getName(), className,
				structure.getFeeCategory().getName(), structure.getAmount(), structure.getPaymentFrequency().name(),
				structure.getStatus().name());
	}
}
