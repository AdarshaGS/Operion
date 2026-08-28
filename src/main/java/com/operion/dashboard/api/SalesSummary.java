package com.operion.dashboard.api;

import java.math.BigDecimal;

public record SalesSummary(BigDecimal totalToday, BigDecimal totalThisMonth) {
}
