package com.operion.sales.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SaleLineItemRequest(@NotNull Long itemId, @Positive int quantity, @NotNull @PositiveOrZero BigDecimal unitPrice) {
}
