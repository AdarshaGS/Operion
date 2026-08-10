package com.operion.library;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.identity.Person;
import com.operion.organisation.Campus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the book catalog, copy registry, borrow lifecycle, and fines. Enforces one
 * ACTIVE (BORROWED) BorrowRecord per BookCopy - a physical copy can't be lent to two
 * people at once - and keeps BookCopy.status in sync with issue/return/lost actions.
 */
@Service
public class LibraryService {

	private final BookRepository bookRepository;
	private final BookCopyRepository bookCopyRepository;
	private final BorrowRecordRepository borrowRecordRepository;
	private final FineRepository fineRepository;

	public LibraryService(BookRepository bookRepository, BookCopyRepository bookCopyRepository,
			BorrowRecordRepository borrowRecordRepository, FineRepository fineRepository) {
		this.bookRepository = bookRepository;
		this.bookCopyRepository = bookCopyRepository;
		this.borrowRecordRepository = borrowRecordRepository;
		this.fineRepository = fineRepository;
	}

	public Book createBook(String isbn, String title, String author, String publisher, String category, String edition) {
		return bookRepository.save(new Book(isbn, title, author, publisher, category, edition));
	}

	public Book withdrawBook(Book book) {
		book.withdraw();
		return bookRepository.save(book);
	}

	public BookCopy addCopy(Book book, Campus campus, String accessionNumber, LocalDate acquiredDate) {
		return bookCopyRepository.save(new BookCopy(book, campus, accessionNumber, acquiredDate));
	}

	@Transactional
	public BorrowRecord issue(BookCopy bookCopy, Person borrower, LocalDate borrowedDate, LocalDate dueDate) {
		if (bookCopy.getStatus() != BookCopyStatus.AVAILABLE) {
			throw new IllegalStateException("Book copy " + bookCopy.getId() + " is not available, was " + bookCopy.getStatus());
		}
		bookCopy.changeStatus(BookCopyStatus.BORROWED);
		bookCopyRepository.save(bookCopy);
		return borrowRecordRepository.save(new BorrowRecord(bookCopy, borrower, borrowedDate, dueDate));
	}

	@Transactional
	public BorrowRecord returnCopy(BorrowRecord borrowRecord, LocalDate returnedDate) {
		borrowRecord.markReturned(returnedDate);
		borrowRecordRepository.save(borrowRecord);

		BookCopy bookCopy = borrowRecord.getBookCopy();
		bookCopy.changeStatus(BookCopyStatus.AVAILABLE);
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
}
