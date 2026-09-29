package com.operion.inventory.api;

import java.time.LocalDate;

import com.operion.inventory.StockTransfer;

public record StockTransferResponse(Long id, Long itemId, Long fromCampusId, Long toCampusId, int quantity, LocalDate transferDate,
		String remarks) {

	public static StockTransferResponse from(StockTransfer transfer) {
		return new StockTransferResponse(transfer.getId(), transfer.getItem().getId(), transfer.getFromCampus().getId(),
				transfer.getToCampus().getId(), transfer.getQuantity(), transfer.getTransferDate(), transfer.getRemarks());
	}
}
