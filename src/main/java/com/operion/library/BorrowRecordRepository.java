package com.operion.library;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

	Optional<BorrowRecord> findByBookCopyIdAndStatus(Long bookCopyId, BorrowStatus status);

	List<BorrowRecord> findByBorrowerIdAndStatus(Long borrowerId, BorrowStatus status);

	List<BorrowRecord> findByBorrowerIdOrderByBorrowedDateDesc(Long borrowerId);

	List<BorrowRecord> findByStatusOrderByDueDateAsc(BorrowStatus status);

	List<BorrowRecord> findByStatusAndDueDateBeforeOrderByDueDateAsc(BorrowStatus status, LocalDate dueDate);

	long countByStatus(BorrowStatus status);

	long countByStatusAndDueDateBefore(BorrowStatus status, LocalDate dueDate);

	long countByBorrowerIdAndStatus(Long borrowerId, BorrowStatus status);

	boolean existsByBorrowerIdAndStatusAndDueDateBefore(Long borrowerId, BorrowStatus status, LocalDate dueDate);
}
