package com.operion.library.api;

import com.operion.library.Book;

public record BookResponse(Long id, String isbn, String title, String author, String publisher, String category,
		String edition, String status) {

	public static BookResponse from(Book book) {
		return new BookResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(), book.getPublisher(),
				book.getCategory(), book.getEdition(), book.getStatus().name());
	}
}
