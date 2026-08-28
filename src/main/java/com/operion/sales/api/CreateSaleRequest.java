package com.operion.sales.api;

import java.time.LocalDate;
import java.util.List;

public record CreateSaleRequest(Long customerId, Long campusId, LocalDate saleDate, List<SaleLineItemRequest> lines) {
}
