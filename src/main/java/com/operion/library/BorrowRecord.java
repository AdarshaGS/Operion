package com.operion.library;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per checkout - insert-only history, same convention as StudentEnrollment/
 * TripLog. Borrower is a bare Person, not Student-specific - staff borrow too, per the
 * light sketch. One ACTIVE (BORROWED) record per BookCopy is enforced in
 * LibraryService, same "one active row" convention as is_current / transport
 * assignments elsewhere in this codebase.
 */
@Getter
@Entity
@Table(name = "borrow_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BorrowRecord extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "book_copy_id")
	private BookCopy bookCopy;

	@ManyToOne(optional = false)
	@JoinColumn(name = "borrower_person_id")
	private Person borrower;

	@Column(name = "borrowed_date", nullable = false)
	private LocalDate borrowedDate;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	/** Nullable - null while still borrowed. */
	@Column(name = "returned_date")
	private LocalDate returnedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BorrowStatus status;

	public BorrowRecord(BookCopy bookCopy, Person borrower, LocalDate borrowedDate, LocalDate dueDate) {
		this.bookCopy = bookCopy;
		this.borrower = borrower;
		this.borrowedDate = borrowedDate;
		this.dueDate = dueDate;
		this.status = BorrowStatus.BORROWED;
	}

	public void markReturned(LocalDate returnedDate) {
		if (status != BorrowStatus.BORROWED) {
			throw new IllegalStateException("Only a borrowed record can be returned, was " + status);
		}
		this.status = BorrowStatus.RETURNED;
		this.returnedDate = returnedDate;
	}

	public void markLost() {
		if (status != BorrowStatus.BORROWED) {
			throw new IllegalStateException("Only a borrowed record can be marked lost, was " + status);
		}
		this.status = BorrowStatus.LOST;
	}
}
