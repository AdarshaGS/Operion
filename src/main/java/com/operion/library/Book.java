package com.operion.library;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Org-wide catalog entry - not campus-scoped, unlike BookCopy. A title exists once per
 * org even if multiple campuses hold copies, same catalog-vs-instance split as
 * FeeCategory/FeeStructure or Route/Vehicle. isbn is nullable and not unique - older
 * library items often lack one, and over-constraining it isn't worth the friction for
 * a school library (per the light sketch's "resist an over-rich model" caution).
 */
@Getter
@Entity
@Table(name = "books")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends TenantScopedEntity {

	/** Nullable. */
	private String isbn;

	@Column(nullable = false)
	private String title;

	/** Nullable. */
	private String author;

	private String publisher;

	private String category;

	private String edition;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookStatus status;

	public Book(String isbn, String title, String author, String publisher, String category, String edition) {
		this.isbn = isbn;
		this.title = title;
		this.author = author;
		this.publisher = publisher;
		this.category = category;
		this.edition = edition;
		this.status = BookStatus.ACTIVE;
	}

	public void withdraw() {
		this.status = BookStatus.WITHDRAWN;
	}
}
