package com.operion.inventory.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.inventory.StockIssue;

public record StockIssueResponse(Long id, Long itemId, Long campusId, int quantity, LocalDate issuedDate, String issuedTo,
		String purpose, BigDecimal unitCost, Long departmentId, String departmentName, Long recipientPersonId, String recipientPersonName,
		Long recipientStudentId, String recipientStudentName, String remarks) {

	public static StockIssueResponse from(StockIssue issue) {
		return new StockIssueResponse(issue.getId(), issue.getItem().getId(), issue.getCampus().getId(), issue.getQuantity(),
				issue.getIssuedDate(), issue.getIssuedTo(), issue.getPurpose(), issue.getUnitCost(),
				issue.getDepartment() != null ? issue.getDepartment().getId() : null,
				issue.getDepartment() != null ? issue.getDepartment().getName() : null,
				issue.getRecipientPerson() != null ? issue.getRecipientPerson().getId() : null,
				issue.getRecipientPerson() != null
						? issue.getRecipientPerson().getFirstName() + " " + issue.getRecipientPerson().getLastName()
						: null,
				issue.getRecipientStudent() != null ? issue.getRecipientStudent().getId() : null,
				issue.getRecipientStudent() != null
						? issue.getRecipientStudent().getPerson().getFirstName() + " " + issue.getRecipientStudent().getPerson().getLastName()
						: null,
				issue.getRemarks());
	}
}
