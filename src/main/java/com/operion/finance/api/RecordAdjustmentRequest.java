package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordAdjustmentRequest(Long invoiceId, BigDecimal amount, String reason, Long approvedBy, LocalDate adjustmentDate) {
}
