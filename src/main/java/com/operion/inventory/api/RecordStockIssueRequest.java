package com.operion.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordStockIssueRequest(Long itemId, Long campusId, int quantity, LocalDate issuedDate, String issuedTo,
		String purpose, BigDecimal unitCost, Long departmentId, Long recipientPersonId, Long recipientStudentId, String remarks) {
}
