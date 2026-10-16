package com.operion.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecordSalePaymentRequest(@NotBlank String paymentMethod, @NotNull @Positive(message = "Payment amount must be positive") BigDecimal amount, @NotNull LocalDate paidAt) {
}
