package com.operion.library.api;

import java.util.List;

import com.operion.library.Book;
import com.operion.library.BookCopy;
import com.operion.library.BookCopyRepository;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/books")
public class BookController {

	private final LibraryService libraryService;
	private final BookRepository bookRepository;
	private final BookCopyRepository bookCopyRepository;
	private final CampusRepository campusRepository;

	public BookController(LibraryService libraryService, BookRepository bookRepository, BookCopyRepository bookCopyRepository,
			CampusRepository campusRepository) {
		this.libraryService = libraryService;
		this.bookRepository = bookRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.campusRepository = campusRepository;
	}

	@PostMapping
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
	public BookResponse withdraw(@PathVariable Long id) {
		return BookResponse.from(libraryService.withdrawBook(findBook(id)));
	}

	@PostMapping("/{id}/copies")
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

	private Book findBook(Long id) {
		return bookRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No book with id " + id));
	}
}
