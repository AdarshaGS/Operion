package com.operion.purchase.api;

import java.math.BigDecimal;

/** unitCost is nullable - omit to fall back to the line's ordered unit cost. */
public record ReceiveGoodsLineRequest(Long lineId, int quantity, BigDecimal unitCost) {
}
