package com.operion.purchase.api;

import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseOrderRequest(Long supplierId, Long campusId, LocalDate expectedDate, List<PurchaseOrderLineItemRequest> lines) {
}
