package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordRefundRequest(Long paymentId, Long invoiceId, BigDecimal amount, String reason, Long approvedBy, LocalDate refundDate) {
}
