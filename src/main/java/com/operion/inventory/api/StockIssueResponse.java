package com.operion.inventory.api;

import java.time.LocalDate;

import com.operion.inventory.StockIssue;

public record StockIssueResponse(Long id, Long itemId, Long campusId, int quantity, LocalDate issuedDate, String issuedTo,
		String purpose, String remarks) {

	public static StockIssueResponse from(StockIssue issue) {
		return new StockIssueResponse(issue.getId(), issue.getItem().getId(), issue.getCampus().getId(), issue.getQuantity(),
				issue.getIssuedDate(), issue.getIssuedTo(), issue.getPurpose(), issue.getRemarks());
	}
}
