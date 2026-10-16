package com.operion.organisation.rollover;

import java.math.BigDecimal;
import java.util.Set;

/** {@code steps} empty or null means every available step. */
public record RolloverRequest(Long sourceYearId, Set<String> steps, BigDecimal feeIncreasePercent) {
}
