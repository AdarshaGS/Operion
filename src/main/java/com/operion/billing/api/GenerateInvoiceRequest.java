package com.operion.billing.api;

import java.time.LocalDate;

public record GenerateInvoiceRequest(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
}
