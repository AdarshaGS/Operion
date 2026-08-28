package com.operion.purchase.api;

import java.time.LocalDate;

import com.operion.purchase.PurchaseReturn;

public record PurchaseReturnResponse(Long id, Long purchaseOrderLineId, int quantity, String reason, LocalDate returnDate, String remarks) {

	public static PurchaseReturnResponse from(PurchaseReturn purchaseReturn) {
		return new PurchaseReturnResponse(purchaseReturn.getId(), purchaseReturn.getPurchaseOrderLine().getId(), purchaseReturn.getQuantity(),
				purchaseReturn.getReason().name(), purchaseReturn.getReturnDate(), purchaseReturn.getRemarks());
	}
}
