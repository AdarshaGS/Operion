package com.operion.purchase.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecordPurchaseReturnRequest(@Positive int quantity, @NotBlank String reason, @NotNull LocalDate returnDate, String remarks) {
}
