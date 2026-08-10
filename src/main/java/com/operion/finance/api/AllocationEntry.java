package com.operion.finance.api;

import java.math.BigDecimal;

public record AllocationEntry(Long invoiceId, BigDecimal amount) {
}
