package com.operion.purchase.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PurchaseOrderLineItemRequest(@NotNull Long itemId, @Positive int quantity, @NotNull @PositiveOrZero BigDecimal unitCost) {
}
