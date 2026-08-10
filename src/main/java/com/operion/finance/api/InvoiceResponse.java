package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.finance.Invoice;

public record InvoiceResponse(Long id, Long academicYearId, Long studentFeeAssignmentId, Long feeStructureInstallmentId,
		String invoiceNumber, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal outstanding, LocalDate dueDate, String status) {

	static InvoiceResponse from(Invoice invoice) {
		return new InvoiceResponse(invoice.getId(), invoice.getAcademicYear().getId(), invoice.getStudentFeeAssignment().getId(),
				invoice.getFeeStructureInstallment().getId(), invoice.getInvoiceNumber(), invoice.getTotalAmount(),
				invoice.getAmountPaid(), invoice.getOutstanding(), invoice.getDueDate(), invoice.getStatus().name());
	}
}
