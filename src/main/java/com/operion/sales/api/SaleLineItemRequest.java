package com.operion.sales.api;

import java.math.BigDecimal;

public record SaleLineItemRequest(Long itemId, int quantity, BigDecimal unitPrice) {
}
