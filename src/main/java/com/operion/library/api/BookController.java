package com.operion.library.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import com.operion.library.Book;
import com.operion.library.BookCopy;
import com.operion.library.BookCopyRepository;
import com.operion.library.BookImportService;
import com.operion.library.BookRepository;
import com.operion.library.BookStatus;
import com.operion.library.LibraryService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/library/books")
@RequirePermission("LIBRARY_VIEW")
public class BookController {

	private final LibraryService libraryService;
	private final BookRepository bookRepository;
	private final BookCopyRepository bookCopyRepository;
	private final CampusRepository campusRepository;
	private final BookImportService bookImportService;

	public BookController(LibraryService libraryService, BookRepository bookRepository, BookCopyRepository bookCopyRepository,
			CampusRepository campusRepository, BookImportService bookImportService) {
		this.libraryService = libraryService;
		this.bookRepository = bookRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.campusRepository = campusRepository;
		this.bookImportService = bookImportService;
	}

	@PostMapping
	@RequirePermission("LIBRARY_CATALOG_MANAGE")
	public BookResponse create(@RequestBody CreateBookRequest request) {
		Book book = libraryService.createBook(request.isbn(), request.title(), request.author(), request.publisher(),
				request.category(), request.edition());
		return BookResponse.from(book);
	}

	@GetMapping
	public List<BookResponse> list() {
		return bookRepository.findByStatus(BookStatus.ACTIVE).stream().map(BookResponse::from).toList();
	}

	@PostMapping("/{id}/withdraw")
	@RequirePermission("LIBRARY_CATALOG_MANAGE")
	public BookResponse withdraw(@PathVariable Long id) {
		return BookResponse.from(libraryService.withdrawBook(findBook(id)));
	}

	@PostMapping("/{id}/copies")
	@RequirePermission("LIBRARY_CATALOG_MANAGE")
	public BookCopyResponse addCopy(@PathVariable Long id, @RequestBody AddBookCopyRequest request) {
		Book book = findBook(id);
		Campus campus = campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		BookCopy copy = libraryService.addCopy(book, campus, request.accessionNumber(), request.acquiredDate());
		return BookCopyResponse.from(copy);
	}

	@GetMapping("/{id}/copies")
	public List<BookCopyResponse> listCopies(@PathVariable Long id) {
		return bookCopyRepository.findByBookId(id).stream().map(BookCopyResponse::from).toList();
	}

	/** Bulk CSV/Excel import (Imports & exports rebuild), same validate-then-confirm shape
	 * as StudentController's import endpoint - see BookImportService/BookRowImportService
	 * for the per-row transaction isolation. */
	@PostMapping("/import")
	@RequirePermission("LIBRARY_CATALOG_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(defaultValue = "false") boolean validateOnly) {
		return bookImportService.importFile(file, validateOnly);
	}

	/** Inherits this controller's class-level LIBRARY_VIEW gate. */
	@GetMapping("/export")
	public List<BookExportResponse> export() {
		return bookRepository.findAll().stream().map(BookExportResponse::from).toList();
	}

	private Book findBook(Long id) {
		return bookRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No book with id " + id));
	}
}
