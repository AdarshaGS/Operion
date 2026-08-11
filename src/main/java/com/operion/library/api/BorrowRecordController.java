package com.operion.library.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.library.BookCopy;
import com.operion.library.BookCopyRepository;
import com.operion.library.BorrowRecord;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.BorrowStatus;
import com.operion.library.LibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/borrow-records")
@RequirePermission("LIBRARY_VIEW")
public class BorrowRecordController {

	private final LibraryService libraryService;
	private final BorrowRecordRepository borrowRecordRepository;
	private final BookCopyRepository bookCopyRepository;
	private final PersonRepository personRepository;

	public BorrowRecordController(LibraryService libraryService, BorrowRecordRepository borrowRecordRepository,
			BookCopyRepository bookCopyRepository, PersonRepository personRepository) {
		this.libraryService = libraryService;
		this.borrowRecordRepository = borrowRecordRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse issue(@RequestBody IssueBookRequest request) {
		BookCopy bookCopy = bookCopyRepository.findById(request.bookCopyId())
				.orElseThrow(() -> new IllegalArgumentException("No book copy with id " + request.bookCopyId()));
		Person borrower = personRepository.findById(request.borrowerPersonId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.borrowerPersonId()));
		BorrowRecord record = libraryService.issue(bookCopy, borrower, request.borrowedDate(), request.dueDate());
		return BorrowRecordResponse.from(record);
	}

	@GetMapping
	public List<BorrowRecordResponse> byBorrower(@RequestParam Long borrowerPersonId) {
		return borrowRecordRepository.findByBorrowerIdAndStatus(borrowerPersonId, BorrowStatus.BORROWED).stream()
				.map(BorrowRecordResponse::from)
				.toList();
	}

	@PostMapping("/{id}/return")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse returnCopy(@PathVariable Long id, @RequestBody ReturnBookRequest request) {
		return BorrowRecordResponse.from(libraryService.returnCopy(findRecord(id), request.returnedDate()));
	}

	@PostMapping("/{id}/mark-lost")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse markLost(@PathVariable Long id) {
		return BorrowRecordResponse.from(libraryService.markLost(findRecord(id)));
	}

	private BorrowRecord findRecord(Long id) {
		return borrowRecordRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No borrow record with id " + id));
	}
}
