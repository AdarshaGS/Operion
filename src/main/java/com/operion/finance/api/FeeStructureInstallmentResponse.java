package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.FeeStructureInstallment;

public record FeeStructureInstallmentResponse(Long id, int installmentNumber, LocalDate dueDate, BigDecimal amount) {

	static FeeStructureInstallmentResponse from(FeeStructureInstallment installment) {
		return new FeeStructureInstallmentResponse(
				installment.getId(), installment.getInstallmentNumber(), installment.getDueDate(), installment.getAmount());
	}
}
