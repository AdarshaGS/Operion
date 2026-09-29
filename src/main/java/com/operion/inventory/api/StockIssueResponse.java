package com.operion.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.inventory.StockIssue;

public record StockIssueResponse(Long id, Long itemId, Long campusId, int quantity, LocalDate issuedDate, String issuedTo,
		String purpose, BigDecimal unitCost, String remarks) {

	public static StockIssueResponse from(StockIssue issue) {
		return new StockIssueResponse(issue.getId(), issue.getItem().getId(), issue.getCampus().getId(), issue.getQuantity(),
				issue.getIssuedDate(), issue.getIssuedTo(), issue.getPurpose(), issue.getUnitCost(), issue.getRemarks());
	}
}
