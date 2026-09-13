package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A manual correction to what's owed on an invoice (e.g. a billing error) - additive/
 * insert-only, same shape as Refund but with no Payment link (nothing was received or
 * returned, so there's nothing to reverse). amount may be positive (owed more) or
 * negative (owed less); Invoice.applyAdjustment() folds it into totalAmount rather than
 * this row ever being edited. Per #131.
 */
@Getter
@Entity
@Table(name = "adjustments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adjustment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "approved_by", nullable = false)
	private Long approvedBy;

	@Column(name = "adjustment_date", nullable = false)
	private LocalDate adjustmentDate;

	public Adjustment(Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate adjustmentDate) {
		this.invoice = invoice;
		this.amount = amount;
		this.reason = reason;
		this.approvedBy = approvedBy;
		this.adjustmentDate = adjustmentDate;
	}
}
