package com.operion.library.api;

import com.operion.library.Book;

public record BookExportResponse(Long id, String isbn, String title, String author, String publisher, String category,
		String edition, String status) {

	public static BookExportResponse from(Book book) {
		return new BookExportResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(), book.getPublisher(),
				book.getCategory(), book.getEdition(), book.getStatus().name());
	}
}
