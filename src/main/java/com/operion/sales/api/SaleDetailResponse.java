package com.operion.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.sales.Sale;

public record SaleDetailResponse(Long id, Long customerId, Long campusId, String receiptNumber, LocalDate saleDate, BigDecimal totalAmount,
		BigDecimal amountPaid, String status, List<SaleLineResponse> lines, List<SalePaymentResponse> payments) {

	public static SaleDetailResponse from(Sale sale, List<SaleLineResponse> lines, List<SalePaymentResponse> payments) {
		return new SaleDetailResponse(sale.getId(), sale.getCustomer().getId(), sale.getCampus().getId(), sale.getReceiptNumber(),
				sale.getSaleDate(), sale.getTotalAmount(), sale.getAmountPaid(), sale.getStatus().name(), lines, payments);
	}
}
