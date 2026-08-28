package com.operion.purchase.api;

import java.time.LocalDate;
import java.util.List;

import com.operion.purchase.PurchaseOrder;

public record PurchaseOrderDetailResponse(Long id, Long supplierId, Long campusId, LocalDate expectedDate, String status,
		List<PurchaseOrderLineResponse> lines) {

	public static PurchaseOrderDetailResponse from(PurchaseOrder order, List<PurchaseOrderLineResponse> lines) {
		return new PurchaseOrderDetailResponse(order.getId(), order.getSupplier().getId(), order.getCampus().getId(), order.getExpectedDate(),
				order.getStatus().name(), lines);
	}
}
