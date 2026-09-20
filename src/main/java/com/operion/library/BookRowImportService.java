package com.operion.library;

import java.util.Map;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same REQUIRES_NEW isolation as StudentRowImportService.
 * Throws on any row-level failure rather than catching internally; BookImportService is
 * what converts the exception into an ImportRowResult.
 */
@Service
public class BookRowImportService {

	private final BookRepository bookRepository;
	private final LibraryService libraryService;

	public BookRowImportService(BookRepository bookRepository, LibraryService libraryService) {
		this.bookRepository = bookRepository;
		this.libraryService = libraryService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String title = require(row, "title");
		String isbn = blankToNull(row.get("isbn"));

		if (isbn != null) {
			var existing = bookRepository.findByIsbn(isbn);
			if (existing.isPresent()) {
				return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE, "Book with isbn '" + isbn + "' already exists",
						existing.get().getId());
			}
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Valid", null);
		}

		Book book = libraryService.createBook(isbn, title, blankToNull(row.get("author")), blankToNull(row.get("publisher")),
				blankToNull(row.get("category")), blankToNull(row.get("edition")));
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", book.getId());
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
