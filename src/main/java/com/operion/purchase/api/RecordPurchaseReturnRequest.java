package com.operion.purchase.api;

import java.time.LocalDate;

public record RecordPurchaseReturnRequest(int quantity, String reason, LocalDate returnDate, String remarks) {
}
