package com.operion.sales.api;

import java.math.BigDecimal;

import com.operion.sales.SaleLine;

public record SaleLineResponse(Long id, Long itemId, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

	public static SaleLineResponse from(SaleLine line) {
		return new SaleLineResponse(line.getId(), line.getItem().getId(), line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
	}
}
