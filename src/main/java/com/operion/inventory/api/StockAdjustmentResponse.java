package com.operion.inventory.api;

import java.time.LocalDate;

import com.operion.inventory.StockAdjustment;

public record StockAdjustmentResponse(Long id, Long itemId, Long campusId, int quantityDelta, String reason,
		LocalDate adjustmentDate, String remarks) {

	public static StockAdjustmentResponse from(StockAdjustment adjustment) {
		return new StockAdjustmentResponse(adjustment.getId(), adjustment.getItem().getId(), adjustment.getCampus().getId(),
				adjustment.getQuantityDelta(), adjustment.getReason().name(), adjustment.getAdjustmentDate(), adjustment.getRemarks());
	}
}
