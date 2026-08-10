package com.operion.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentEntry(int installmentNumber, LocalDate dueDate, BigDecimal amount) {
}
