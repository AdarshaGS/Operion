package com.operion.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** amount is required and must be greater than zero; it is billed as entered. */
public record GenerateInvoiceRequest(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal amount) {
}
