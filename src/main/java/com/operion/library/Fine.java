package com.operion.library;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
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
 * A discretionary charge against a BorrowRecord, raised explicitly by a librarian -
 * deliberately standalone, not wired into the Fees module's Invoice/Payment, per the
 * design sign-off: fine amounts are school-specific/discretionary (no per-day rate
 * engine) and staff borrowers have no StudentEnrollment to tie a fee assignment to.
 * Multiple fines can exist per BorrowRecord (e.g. overdue + damage). amount is
 * BigDecimal, not Double - money-correctness rule carried over from Fees.
 */
@Getter
@Entity
@Table(name = "fines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fine extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "borrow_record_id")
	private BorrowRecord borrowRecord;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FineReason reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FineStatus status;

	/** Nullable - set only once paid. */
	@Column(name = "paid_date")
	private LocalDate paidDate;

	/** Nullable - set only once waived. */
	@Column(name = "waived_by")
	private Long waivedBy;

	@Column(name = "waived_reason")
	private String waivedReason;

	public Fine(BorrowRecord borrowRecord, BigDecimal amount, FineReason reason) {
		this.borrowRecord = borrowRecord;
		this.amount = amount;
		this.reason = reason;
		this.status = FineStatus.PENDING;
	}

	public void pay(LocalDate paidDate) {
		requirePending();
		this.status = FineStatus.PAID;
		this.paidDate = paidDate;
	}

	public void waive(Long waivedBy, String waivedReason) {
		requirePending();
		this.status = FineStatus.WAIVED;
		this.waivedBy = waivedBy;
		this.waivedReason = waivedReason;
	}

	private void requirePending() {
		if (status != FineStatus.PENDING) {
			throw new IllegalStateException("Only a pending fine can be settled, was " + status);
		}
	}
}
