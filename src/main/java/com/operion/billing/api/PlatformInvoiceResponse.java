package com.operion.billing.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.operion.billing.PlatformInvoice;

public record PlatformInvoiceResponse(
		Long id,
		Long organisationId,
		Long subscriptionId,
		LocalDate periodStart,
		LocalDate periodEnd,
		Integer studentCountAtBilling,
		BigDecimal amount,
		String status,
		Instant issuedAt,
		LocalDate dueDate,
		Instant paidAt) {

	public static PlatformInvoiceResponse from(PlatformInvoice invoice) {
		return new PlatformInvoiceResponse(
				invoice.getId(),
				invoice.getOrganisation().getId(),
				invoice.getSubscription().getId(),
				invoice.getPeriodStart(),
				invoice.getPeriodEnd(),
				invoice.getStudentCountAtBilling(),
				invoice.getAmount(),
				invoice.getStatus().name(),
				invoice.getIssuedAt(),
				invoice.getDueDate(),
				invoice.getPaidAt());
	}
}
