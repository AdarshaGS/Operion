package com.operion.dashboard.api;

import java.math.BigDecimal;

public record FeeSummary(BigDecimal totalInvoiced, BigDecimal totalCollected, int collectionRatePercent,
		BigDecimal outstanding, long overdueInvoices) {
}
