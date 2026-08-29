package com.operion.student;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Infrastructure, not a business entity - same reasoning as
 * {@link com.operion.finance.FeeDocumentCounter}. One row per (organisation, calendar
 * year), incremented under StudentAdmissionCounterRepository's pessimistic write lock so
 * admission numbering never races via SELECT MAX()+1. Keyed by calendar year rather than
 * AcademicYear since a Student has no academic-year link at admission time. Per #142.
 */
@Getter
@Entity
@Table(name = "student_admission_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentAdmissionCounter extends TenantScopedEntity {

	@Column(name = "calendar_year", nullable = false)
	private int calendarYear;

	@Column(name = "next_number", nullable = false)
	private long nextNumber;

	public StudentAdmissionCounter(int calendarYear) {
		this.calendarYear = calendarYear;
		this.nextNumber = 1;
	}

	/** Must only be called while holding the repository's pessimistic write lock on this row. */
	public long consumeNext() {
		long number = nextNumber;
		nextNumber++;
		return number;
	}
}
