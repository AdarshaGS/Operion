package com.operion.purchase.api;

import java.math.BigDecimal;

import com.operion.purchase.PurchaseOrderLine;

public record PurchaseOrderLineResponse(Long id, Long itemId, int quantity, BigDecimal unitCost, int quantityReceived, int quantityReturned) {

	public static PurchaseOrderLineResponse from(PurchaseOrderLine line, int quantityReturned) {
		return new PurchaseOrderLineResponse(line.getId(), line.getItem().getId(), line.getQuantity(), line.getUnitCost(),
				line.getQuantityReceived(), quantityReturned);
	}
}
