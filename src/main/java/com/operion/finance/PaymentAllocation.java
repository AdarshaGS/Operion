package com.operion.finance;

import java.math.BigDecimal;

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
 * The real ledger join backing Invoice.amountPaid - a single Payment can cover multiple
 * Invoices (a lump sum covering last term's balance plus this term's installment is
 * routine). This table, not Invoice.amountPaid, is the source of truth the cached balance
 * is reconciled against. Insert-only - a bounce/refund reverses the effect via
 * Invoice.reversePayment, never deletes or mutates this row. Per
 * ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "payment_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "invoice_id")
	private Invoice invoice;

	@Column(name = "allocated_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal allocatedAmount;

	public PaymentAllocation(Payment payment, Invoice invoice, BigDecimal allocatedAmount) {
		this.payment = payment;
		this.invoice = invoice;
		this.allocatedAmount = allocatedAmount;
	}
}
