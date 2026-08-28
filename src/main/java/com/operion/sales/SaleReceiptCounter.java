package com.operion.sales;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Infrastructure, not a business entity - like FeeDocumentCounter, but one row per
 * organisation (Sales has no academic-year/document-type dimension to key on), incremented
 * under SaleReceiptCounterRepository's pessimistic write lock so receipt numbering never
 * races via SELECT MAX()+1.
 */
@Getter
@Entity
@Table(name = "sale_receipt_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReceiptCounter extends TenantScopedEntity {

	@Column(name = "next_number", nullable = false)
	private long nextNumber = 1;

	/** Must only be called while holding the repository's pessimistic write lock on this row. */
	public long consumeNext() {
		long number = nextNumber;
		nextNumber++;
		return number;
	}
}
