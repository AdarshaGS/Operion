package com.operion.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.inventory.StockEntry;

public record StockEntryResponse(Long id, Long itemId, Long campusId, int quantity, BigDecimal unitCost, LocalDate entryDate,
		String source, Long supplierId, String supplierName, String remarks) {

	public static StockEntryResponse from(StockEntry entry) {
		return new StockEntryResponse(entry.getId(), entry.getItem().getId(), entry.getCampus().getId(), entry.getQuantity(),
				entry.getUnitCost(), entry.getEntryDate(), entry.getSource(), entry.getSupplier() != null ? entry.getSupplier().getId() : null,
				entry.getSupplier() != null ? entry.getSupplier().getName() : null, entry.getRemarks());
	}
}
