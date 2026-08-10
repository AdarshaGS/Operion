package com.operion.finance.api;

import java.math.BigDecimal;

public record ReviseAssignmentRequest(BigDecimal discountAmount, String discountReason, Long approvedBy) {
}
