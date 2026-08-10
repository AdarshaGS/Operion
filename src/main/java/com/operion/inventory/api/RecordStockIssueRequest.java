package com.operion.inventory.api;

import java.time.LocalDate;

public record RecordStockIssueRequest(Long itemId, Long campusId, int quantity, LocalDate issuedDate, String issuedTo,
		String purpose, String remarks) {
}
