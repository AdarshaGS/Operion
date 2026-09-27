package com.operion.library.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.library.BookCopy;
import com.operion.library.BookCopyRepository;
import com.operion.library.BorrowRecord;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.BorrowStatus;
import com.operion.library.Fine;
import com.operion.library.FineRepository;
import com.operion.library.FineStatus;
import com.operion.library.LibraryReminderService;
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
	private final FineRepository fineRepository;
	private final LibraryReminderService libraryReminderService;

	public BorrowRecordController(LibraryService libraryService, BorrowRecordRepository borrowRecordRepository,
			BookCopyRepository bookCopyRepository, PersonRepository personRepository, FineRepository fineRepository,
			LibraryReminderService libraryReminderService) {
		this.libraryService = libraryService;
		this.borrowRecordRepository = borrowRecordRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.personRepository = personRepository;
		this.fineRepository = fineRepository;
		this.libraryReminderService = libraryReminderService;
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
	public List<BorrowRecordResponse> activeBorrows(@RequestParam(required = false) Long borrowerPersonId) {
		List<BorrowRecord> records = borrowerPersonId != null
				? borrowRecordRepository.findByBorrowerIdAndStatus(borrowerPersonId, BorrowStatus.BORROWED)
				: borrowRecordRepository.findByStatusOrderByDueDateAsc(BorrowStatus.BORROWED);
		return records.stream().map(BorrowRecordResponse::from).toList();
	}

	/** Active + past loans for one borrower (#248), unlike the org-wide/borrower-filtered
	 * {@code GET} above which only ever returns BORROWED records. */
	@GetMapping("/by-borrower/{personId}")
	public List<BorrowRecordResponse> history(@PathVariable Long personId) {
		return borrowRecordRepository.findByBorrowerIdOrderByBorrowedDateDesc(personId).stream().map(BorrowRecordResponse::from).toList();
	}

	/** All BORROWED records past their due date, with borrower contact and outstanding
	 * fine total, for the dedicated Overdue view (#171). */
	@GetMapping("/overdue")
	public List<OverdueBorrowResponse> overdue() {
		return borrowRecordRepository.findByStatusAndDueDateBeforeOrderByDueDateAsc(BorrowStatus.BORROWED, LocalDate.now()).stream()
				.map(record -> OverdueBorrowResponse.from(record, pendingFineTotal(record.getId())))
				.toList();
	}

	@PostMapping("/{id}/return")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse returnCopy(@PathVariable Long id, @RequestBody ReturnBookRequest request) {
		return BorrowRecordResponse.from(libraryService.returnCopy(findRecord(id), request.returnedDate(), request.isDamaged()));
	}

	@PostMapping("/{id}/mark-lost")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse markLost(@PathVariable Long id) {
		return BorrowRecordResponse.from(libraryService.markLost(findRecord(id)));
	}

	@PostMapping("/{id}/mark-damaged")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public BorrowRecordResponse markDamaged(@PathVariable Long id) {
		return BorrowRecordResponse.from(libraryService.markDamaged(findRecord(id)));
	}

	@PostMapping("/{id}/send-reminder")
	@RequirePermission("LIBRARY_BORROW_MANAGE")
	public int sendReminder(@PathVariable Long id) {
		return libraryReminderService.sendOverdueReminder(findRecord(id));
	}

	private BigDecimal pendingFineTotal(Long borrowRecordId) {
		return fineRepository.findByBorrowRecordId(borrowRecordId).stream()
				.filter(fine -> fine.getStatus() == FineStatus.PENDING)
				.map(Fine::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BorrowRecord findRecord(Long id) {
		return borrowRecordRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No borrow record with id " + id));
	}
}
