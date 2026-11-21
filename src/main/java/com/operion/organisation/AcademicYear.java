package com.operion.organisation;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Foundation owns only this shape (lifecycle + dates); the academic module builds
 * grades/sections/enrolment on top of it via FK, per ai-context/erp-system-plan.md §1.7.6.
 */
@Getter
@Entity
@Table(name = "academic_years", uniqueConstraints = {
		@UniqueConstraint(name = "uq_academic_years_org_current", columnNames = { "organisation_id", "current_marker" }) })
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

	/** Mirrors {@link #current} as TRUE or NULL so a unique key can allow at most one current year per organisation. Kept in step by every mutator below. */
	@Column(name = "current_marker")
	private Boolean currentMarker;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AcademicYearStatus status;

	public AcademicYear(String name, LocalDate startDate, LocalDate endDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
		this.current = false;
		this.currentMarker = null;
		this.status = AcademicYearStatus.DRAFT;
	}

	public boolean isClosed() {
		return status == AcademicYearStatus.CLOSED;
	}

	/** Write paths call this (via AcademicYearGuard) before changing anything scoped to this year. */
	public void requireOpen(String action) {
		if (isClosed()) {
			throw new ClosedAcademicYearException(name, action);
		}
	}

	public void markCurrent() {
		if (isClosed()) {
			throw new IllegalStateException("A closed academic year cannot be made current again");
		}
		this.current = true;
		this.currentMarker = true;
		this.status = AcademicYearStatus.ACTIVE;
	}

	/** Used when a different year is marked current - leaves status untouched, since
	 * ceasing to be "the" current year isn't the same event as this year being done. */
	public void unmarkCurrent() {
		this.current = false;
		this.currentMarker = null;
	}

	public void close() {
		if (status == AcademicYearStatus.CLOSED) {
			throw new IllegalStateException("Academic year is already closed");
		}
		this.current = false;
		this.currentMarker = null;
		this.status = AcademicYearStatus.CLOSED;
	}
}
