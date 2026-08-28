package com.operion.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.sales.Sale;

public record SaleResponse(Long id, Long customerId, Long campusId, String receiptNumber, LocalDate saleDate, BigDecimal totalAmount,
		BigDecimal amountPaid, String status) {

	public static SaleResponse from(Sale sale) {
		return new SaleResponse(sale.getId(), sale.getCustomer().getId(), sale.getCampus().getId(), sale.getReceiptNumber(), sale.getSaleDate(),
				sale.getTotalAmount(), sale.getAmountPaid(), sale.getStatus().name());
	}
}
