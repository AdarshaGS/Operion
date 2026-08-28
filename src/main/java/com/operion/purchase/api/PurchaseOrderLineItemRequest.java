package com.operion.purchase.api;

import java.math.BigDecimal;

public record PurchaseOrderLineItemRequest(Long itemId, int quantity, BigDecimal unitCost) {
}
