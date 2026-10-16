package com.operion.purchase.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** unitCost is nullable - omit to fall back to the line's ordered unit cost. */
public record ReceiveGoodsLineRequest(@NotNull Long lineId, @Positive int quantity, @PositiveOrZero BigDecimal unitCost) {
}
