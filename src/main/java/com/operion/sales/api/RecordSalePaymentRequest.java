package com.operion.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordSalePaymentRequest(String paymentMethod, BigDecimal amount, LocalDate paidAt) {
}
