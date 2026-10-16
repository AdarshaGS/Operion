package com.operion.purchase.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreatePurchaseOrderRequest(@NotNull Long supplierId, @NotNull Long campusId, @NotNull LocalDate expectedDate,
		@NotEmpty @Valid List<PurchaseOrderLineItemRequest> lines) {
}
