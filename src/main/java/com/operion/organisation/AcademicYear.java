package com.operion.organisation;

import java.time.LocalDate;

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
 * Foundation owns only this shape (lifecycle + dates); the academic module builds
 * grades/sections/enrolment on top of it via FK, per ai-context/erp-system-plan.md §1.7.6.
 */
@Getter
@Entity
@Table(name = "academic_years")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicYear extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "is_current", nullable = false)
	private boolean current;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AcademicYearStatus status;

	public AcademicYear(String name, LocalDate startDate, LocalDate endDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
		this.current = false;
		this.status = AcademicYearStatus.DRAFT;
	}

	public void markCurrent() {
		this.current = true;
		this.status = AcademicYearStatus.ACTIVE;
	}

	/** Used when a different year is marked current - leaves status untouched, since
	 * ceasing to be "the" current year isn't the same event as this year being done. */
	public void unmarkCurrent() {
		this.current = false;
	}

	public void close() {
		if (status == AcademicYearStatus.CLOSED) {
			throw new IllegalStateException("Academic year is already closed");
		}
		this.current = false;
		this.status = AcademicYearStatus.CLOSED;
	}
}
