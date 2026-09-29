package com.operion.inventory.api;

import java.time.LocalDate;

public record RecordStockTransferRequest(Long itemId, Long fromCampusId, Long toCampusId, int quantity, LocalDate transferDate,
		String remarks) {
}
