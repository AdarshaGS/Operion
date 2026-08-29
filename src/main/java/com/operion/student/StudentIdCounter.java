package com.operion.student;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Infrastructure, not a business entity - same convention as FeeDocumentCounter/
 * SaleReceiptCounter. One row per (organisation, calendar year), incremented under
 * StudentIdCounterRepository's pessimistic write lock so the system-generated student ID
 * (distinct from the user-typed admission number) never races via SELECT MAX()+1.
 */
@Getter
@Entity
@Table(name = "student_id_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentIdCounter extends TenantScopedEntity {

	/** Column isn't just "year" - a reserved word on some SQL dialects (e.g. H2, used in tests). */
	@Column(name = "counter_year", nullable = false)
	private int year;

	@Column(name = "next_number", nullable = false)
	private long nextNumber;

	public StudentIdCounter(int year) {
		this.year = year;
		this.nextNumber = 1;
	}

	/** Must only be called while holding the repository's pessimistic write lock on this row. */
	public long consumeNext() {
		long number = nextNumber;
		nextNumber++;
		return number;
	}
}
