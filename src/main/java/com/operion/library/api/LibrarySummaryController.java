package com.operion.library.api;

import java.time.LocalDate;

import com.operion.authorization.RequirePermission;
import com.operion.library.BookCopyRepository;
import com.operion.library.BookCopyStatus;
import com.operion.library.BorrowRecordRepository;
import com.operion.library.BorrowStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backs the Available/Issued/Overdue/Lost/Damaged tab counts on LibraryPage (#172) -
 * reuses the same countByStatus-style queries the org-wide dashboard tile already uses. */
@RestController
@RequestMapping("/api/v1/library/summary")
@RequirePermission("LIBRARY_VIEW")
public class LibrarySummaryController {

	private final BookCopyRepository bookCopyRepository;
	private final BorrowRecordRepository borrowRecordRepository;

	public LibrarySummaryController(BookCopyRepository bookCopyRepository, BorrowRecordRepository borrowRecordRepository) {
		this.bookCopyRepository = bookCopyRepository;
		this.borrowRecordRepository = borrowRecordRepository;
	}

	@GetMapping
	public LibraryStatusSummaryResponse get() {
		long overdue = borrowRecordRepository.countByStatusAndDueDateBefore(BorrowStatus.BORROWED, LocalDate.now());
		return new LibraryStatusSummaryResponse(bookCopyRepository.countByStatus(BookCopyStatus.AVAILABLE),
				bookCopyRepository.countByStatus(BookCopyStatus.BORROWED), overdue,
				bookCopyRepository.countByStatus(BookCopyStatus.LOST), bookCopyRepository.countByStatus(BookCopyStatus.DAMAGED));
	}
}
