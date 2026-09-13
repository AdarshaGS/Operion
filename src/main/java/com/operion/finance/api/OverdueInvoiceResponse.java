package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.operion.finance.Invoice;
import com.operion.student.StudentEnrollment;

public record OverdueInvoiceResponse(Long id, String invoiceNumber, Long studentEnrollmentId, Long studentId, Long schoolClassId,
		BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal outstanding, LocalDate dueDate, long daysOverdue) {

	static OverdueInvoiceResponse from(Invoice invoice, LocalDate asOf) {
		StudentEnrollment enrollment = invoice.getStudentFeeAssignment().getStudentEnrollment();
		return new OverdueInvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), enrollment.getId(), enrollment.getStudent().getId(),
				enrollment.getSection().getSchoolClass().getId(), invoice.getTotalAmount(), invoice.getAmountPaid(), invoice.getOutstanding(),
				invoice.getDueDate(), ChronoUnit.DAYS.between(invoice.getDueDate(), asOf));
	}
}
