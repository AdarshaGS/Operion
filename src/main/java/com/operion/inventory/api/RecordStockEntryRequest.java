package com.operion.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordStockEntryRequest(Long itemId, Long campusId, int quantity, BigDecimal unitCost, LocalDate entryDate,
		String source, String remarks) {
}
