package com.operion.sales.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateSaleRequest(@NotNull Long customerId, @NotNull Long campusId, @NotNull LocalDate saleDate,
		@NotEmpty @Valid List<SaleLineItemRequest> lines) {
}
