package com.operion.finance;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
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
 * Infrastructure, not a business entity - like a DB sequence, not the generic-catch-all
 * anti-pattern the project avoids elsewhere. One row per (organisation, academic year,
 * document type), incremented under FeeDocumentCounterRepository's pessimistic write lock
 * so invoice/receipt numbering never races via SELECT MAX()+1. Per
 * ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "fee_document_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeDocumentCounter extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private FeeDocumentType documentType;

	@Column(name = "next_number", nullable = false)
	private long nextNumber;

	public FeeDocumentCounter(AcademicYear academicYear, FeeDocumentType documentType) {
		this.academicYear = academicYear;
		this.documentType = documentType;
		this.nextNumber = 1;
	}

	/** Must only be called while holding the repository's pessimistic write lock on this row. */
	public long consumeNext() {
		long number = nextNumber;
		nextNumber++;
		return number;
	}
}
