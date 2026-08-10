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
 * Always references an original Payment and the Invoice it reduces - an additive
 * reversing event, never an edit/delete of the original Payment. Per
 * ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "refunds")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "approved_by", nullable = false)
	private Long approvedBy;

	@Column(name = "refund_date", nullable = false)
	private LocalDate refundDate;

	public Refund(Payment payment, Invoice invoice, BigDecimal amount, String reason, Long approvedBy, LocalDate refundDate) {
		this.payment = payment;
		this.invoice = invoice;
		this.amount = amount;
		this.reason = reason;
		this.approvedBy = approvedBy;
		this.refundDate = refundDate;
	}
}
