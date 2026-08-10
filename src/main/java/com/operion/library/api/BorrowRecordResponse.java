package com.operion.library.api;

import java.time.LocalDate;

import com.operion.library.BorrowRecord;

public record BorrowRecordResponse(Long id, Long bookCopyId, Long borrowerPersonId, LocalDate borrowedDate,
		LocalDate dueDate, LocalDate returnedDate, String status) {

	public static BorrowRecordResponse from(BorrowRecord borrowRecord) {
		return new BorrowRecordResponse(borrowRecord.getId(), borrowRecord.getBookCopy().getId(),
				borrowRecord.getBorrower().getId(), borrowRecord.getBorrowedDate(), borrowRecord.getDueDate(),
				borrowRecord.getReturnedDate(), borrowRecord.getStatus().name());
	}
}
