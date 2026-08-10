package com.operion.finance.api;

import java.math.BigDecimal;
import java.util.List;

public record CreateFeeStructureRequest(
		Long academicYearId, Long schoolClassId, Long feeCategoryId, BigDecimal amount, List<InstallmentEntry> installments) {
}
