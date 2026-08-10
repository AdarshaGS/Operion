package com.operion.library.api;

import java.time.LocalDate;

import com.operion.library.BookCopy;

public record BookCopyResponse(Long id, Long bookId, Long campusId, String accessionNumber, String status, LocalDate acquiredDate) {

	public static BookCopyResponse from(BookCopy bookCopy) {
		return new BookCopyResponse(bookCopy.getId(), bookCopy.getBook().getId(), bookCopy.getCampus().getId(),
				bookCopy.getAccessionNumber(), bookCopy.getStatus().name(), bookCopy.getAcquiredDate());
	}
}
