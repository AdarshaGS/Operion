package com.operion.performance.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public record ConfigureKpiRequest(boolean enabled, @NotNull Integer weight, @NotNull BigDecimal target, Integer days) {
}
