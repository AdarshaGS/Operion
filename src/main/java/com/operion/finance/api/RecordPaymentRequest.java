package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecordPaymentRequest(Long academicYearId, BigDecimal amount, String paymentMethod, LocalDate paymentDate,
		String remarks, List<AllocationEntry> allocations) {
}
