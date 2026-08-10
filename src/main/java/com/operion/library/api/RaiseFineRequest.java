package com.operion.library.api;

import java.math.BigDecimal;

public record RaiseFineRequest(Long borrowRecordId, BigDecimal amount, String reason) {
}
