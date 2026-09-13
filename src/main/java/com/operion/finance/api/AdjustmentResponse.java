package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Adjustment;

public record AdjustmentResponse(Long id, Long invoiceId, BigDecimal amount, String reason, Long approvedBy, LocalDate adjustmentDate) {

	static AdjustmentResponse from(Adjustment adjustment) {
		return new AdjustmentResponse(adjustment.getId(), adjustment.getInvoice().getId(),
				adjustment.getAmount(), adjustment.getReason(), adjustment.getApprovedBy(), adjustment.getAdjustmentDate());
	}
}
