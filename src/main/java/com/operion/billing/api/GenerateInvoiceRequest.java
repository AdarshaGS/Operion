package com.operion.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** amount is optional: left out, the invoice is the subscription rate x active student headcount; given, it is billed as entered. */
public record GenerateInvoiceRequest(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal amount) {
}
