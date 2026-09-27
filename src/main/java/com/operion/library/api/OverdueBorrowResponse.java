package com.operion.library.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.operion.library.BorrowRecord;

public record OverdueBorrowResponse(Long id, Long borrowerPersonId, String borrowerName, String borrowerPhone,
		String borrowerEmail, String bookTitle, String accessionNumber, LocalDate dueDate, long daysOverdue,
		BigDecimal fineDue) {

	public static OverdueBorrowResponse from(BorrowRecord record, BigDecimal fineDue) {
		return new OverdueBorrowResponse(record.getId(), record.getBorrower().getId(),
				record.getBorrower().getFirstName() + " " + record.getBorrower().getLastName(),
				record.getBorrower().getPhone(), record.getBorrower().getEmail(), record.getBookCopy().getBook().getTitle(),
				record.getBookCopy().getAccessionNumber(), record.getDueDate(),
				ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now()), fineDue);
	}
}
