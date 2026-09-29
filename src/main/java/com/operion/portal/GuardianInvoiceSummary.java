package com.operion.portal;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Invoice;

public record GuardianInvoiceSummary(
		Long invoiceId, String invoiceNumber, LocalDate dueDate, BigDecimal totalAmount, BigDecimal amountPaid,
		BigDecimal outstanding, String status) {

	public static GuardianInvoiceSummary from(Invoice invoice) {
		return new GuardianInvoiceSummary(invoice.getId(), invoice.getInvoiceNumber(), invoice.getDueDate(), invoice.getTotalAmount(),
				invoice.getAmountPaid(), invoice.getTotalAmount().subtract(invoice.getAmountPaid()), invoice.getStatus().name());
	}
}
