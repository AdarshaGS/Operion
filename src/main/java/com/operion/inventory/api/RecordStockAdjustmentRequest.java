package com.operion.inventory.api;

import java.time.LocalDate;

public record RecordStockAdjustmentRequest(Long itemId, Long campusId, int quantityDelta, String reason, LocalDate adjustmentDate,
		String remarks) {
}
