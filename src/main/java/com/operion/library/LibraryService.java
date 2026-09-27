package com.operion.library;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantContext;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the book catalog, copy registry, borrow lifecycle, and fines. Enforces one
 * ACTIVE (BORROWED) BorrowRecord per BookCopy - a physical copy can't be lent to two
 * people at once - and keeps BookCopy.status in sync with issue/return/lost actions.
 * Borrower type (student vs. staff, for #169's per-type policy) is read off whether the
 * borrower Person has a StaffProfile - no role-name hardcoding, same "existence of a
 * role-specific profile" signal StudentRepository/StaffProfileRepository already expose
 * elsewhere.
 */
@Service
public class LibraryService {

	private final BookRepository bookRepository;
	private final BookCopyRepository bookCopyRepository;
	private final BorrowRecordRepository borrowRecordRepository;
	private final FineRepository fineRepository;
	private final LibrarySettingsRepository librarySettingsRepository;
	private final StaffProfileRepository staffProfileRepository;

	public LibraryService(BookRepository bookRepository, BookCopyRepository bookCopyRepository,
			BorrowRecordRepository borrowRecordRepository, FineRepository fineRepository,
			LibrarySettingsRepository librarySettingsRepository, StaffProfileRepository staffProfileRepository) {
		this.bookRepository = bookRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.borrowRecordRepository = borrowRecordRepository;
		this.fineRepository = fineRepository;
		this.librarySettingsRepository = librarySettingsRepository;
		this.staffProfileRepository = staffProfileRepository;
	}

	public Book createBook(String isbn, String title, String author, String publisher, String category, String edition) {
		return bookRepository.save(new Book(isbn, title, author, publisher, category, edition));
	}

	public Book updateBook(Book book, String isbn, String title, String author, String publisher, String category, String edition) {
		book.update(isbn, title, author, publisher, category, edition);
		return bookRepository.save(book);
	}

	public Book withdrawBook(Book book) {
		book.withdraw();
		return bookRepository.save(book);
	}

	public BookCopy addCopy(Book book, Campus campus, String accessionNumber, LocalDate acquiredDate, String shelfLocation) {
		return bookCopyRepository.save(new BookCopy(book, campus, accessionNumber, acquiredDate, shelfLocation));
	}

	public BookCopy updateCopyShelfLocation(BookCopy bookCopy, String shelfLocation) {
		bookCopy.updateShelfLocation(shelfLocation);
		return bookCopyRepository.save(bookCopy);
	}

	@Transactional
	public BorrowRecord issue(BookCopy bookCopy, Person borrower, LocalDate borrowedDate, LocalDate dueDate) {
		if (bookCopy.getStatus() != BookCopyStatus.AVAILABLE) {
			throw new IllegalStateException("Book copy " + bookCopy.getId() + " is not available, was " + bookCopy.getStatus());
		}

		boolean staff = staffProfileRepository.findByPersonId(borrower.getId()).isPresent();
		LibrarySettings settings = currentSettings();

		if (settings.isBlockIssueOnOverdue()
				&& borrowRecordRepository.existsByBorrowerIdAndStatusAndDueDateBefore(borrower.getId(), BorrowStatus.BORROWED, LocalDate.now())) {
			throw new IllegalStateException("Borrower " + borrower.getId() + " has an overdue book and cannot be issued another");
		}

		long activeLoans = borrowRecordRepository.countByBorrowerIdAndStatus(borrower.getId(), BorrowStatus.BORROWED);
		int maxLoans = settings.maxLoansFor(staff);
		if (activeLoans >= maxLoans) {
			throw new IllegalStateException("Borrower " + borrower.getId() + " already has " + activeLoans + " book(s) out, the maximum is " + maxLoans);
		}

		LocalDate effectiveDueDate = dueDate != null ? dueDate : borrowedDate.plusDays(settings.borrowingPeriodDaysFor(staff));

		bookCopy.changeStatus(BookCopyStatus.BORROWED);
		bookCopyRepository.save(bookCopy);
		return borrowRecordRepository.save(new BorrowRecord(bookCopy, borrower, borrowedDate, effectiveDueDate));
	}

	@Transactional
	public BorrowRecord returnCopy(BorrowRecord borrowRecord, LocalDate returnedDate, boolean damaged) {
		borrowRecord.markReturned(returnedDate);
		borrowRecordRepository.save(borrowRecord);

		BookCopy bookCopy = borrowRecord.getBookCopy();
		bookCopy.changeStatus(damaged ? BookCopyStatus.DAMAGED : BookCopyStatus.AVAILABLE);
		bookCopyRepository.save(bookCopy);
		return borrowRecord;
	}

	@Transactional
	public BorrowRecord markLost(BorrowRecord borrowRecord) {
		borrowRecord.markLost();
		borrowRecordRepository.save(borrowRecord);

		BookCopy bookCopy = borrowRecord.getBookCopy();
		bookCopy.changeStatus(BookCopyStatus.LOST);
		bookCopyRepository.save(bookCopy);
		return borrowRecord;
	}

	/** Same closure shape as markLost(), for a copy discovered damaged independently of
	 * the return dialog (#168) - closes the loan as returned-today and leaves the copy
	 * DAMAGED rather than AVAILABLE. */
	@Transactional
	public BorrowRecord markDamaged(BorrowRecord borrowRecord) {
		return returnCopy(borrowRecord, LocalDate.now(), true);
	}

	public Fine raiseFine(BorrowRecord borrowRecord, BigDecimal amount, FineReason reason) {
		return fineRepository.save(new Fine(borrowRecord, amount, reason));
	}

	public Fine payFine(Fine fine, LocalDate paidDate) {
		fine.pay(paidDate);
		return fineRepository.save(fine);
	}

	public Fine waiveFine(Fine fine, Long waivedBy, String waivedReason) {
		fine.waive(waivedBy, waivedReason);
		return fineRepository.save(fine);
	}

	private LibrarySettings currentSettings() {
		return librarySettingsRepository.findByOrganisationId(TenantContext.getOrganisationId()).orElseGet(LibrarySettings::new);
	}
}
