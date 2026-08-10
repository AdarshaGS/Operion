package com.operion.library;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
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
 * A physical, campus-scoped copy of a Book. status is maintained by LibraryService as
 * BorrowRecords are issued/returned/marked lost - never hard-deleted, BorrowRecord
 * history references it after it's WITHDRAWN.
 */
@Getter
@Entity
@Table(name = "book_copies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookCopy extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "book_id")
	private Book book;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "accession_number", nullable = false)
	private String accessionNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookCopyStatus status;

	/** Nullable. */
	@Column(name = "acquired_date")
	private LocalDate acquiredDate;

	public BookCopy(Book book, Campus campus, String accessionNumber, LocalDate acquiredDate) {
		this.book = book;
		this.campus = campus;
		this.accessionNumber = accessionNumber;
		this.status = BookCopyStatus.AVAILABLE;
		this.acquiredDate = acquiredDate;
	}

	public void changeStatus(BookCopyStatus status) {
		this.status = status;
	}
}
