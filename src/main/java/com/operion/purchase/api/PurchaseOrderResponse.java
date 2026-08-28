package com.operion.purchase.api;

import java.time.LocalDate;

import com.operion.purchase.PurchaseOrder;

public record PurchaseOrderResponse(Long id, Long supplierId, Long campusId, LocalDate expectedDate, String status) {

	public static PurchaseOrderResponse from(PurchaseOrder order) {
		return new PurchaseOrderResponse(order.getId(), order.getSupplier().getId(), order.getCampus().getId(), order.getExpectedDate(),
				order.getStatus().name());
	}
}
