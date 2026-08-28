package com.operion.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.inventory.Customer;
import com.operion.organisation.Campus;
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
 * A point-of-sale transaction. totalAmount is computed once from line items at creation
 * and stored (same convention as Invoice.totalAmount) - amountPaid/status track payments
 * applied afterwards via SalePayment, same shape as Invoice.amountPaid/applyPayment.
 * Lines reference this by FK only, no owning-side collection - same convention as
 * PurchaseOrderLine referencing PurchaseOrder.
 */
@Getter
@Entity
@Table(name = "sales")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "receipt_number", nullable = false, length = 50)
	private String receiptNumber;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
	private BigDecimal amountPaid;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SaleStatus status;

	public Sale(Customer customer, Campus campus, String receiptNumber, LocalDate saleDate, BigDecimal totalAmount) {
		this.customer = customer;
		this.campus = campus;
		this.receiptNumber = receiptNumber;
		this.saleDate = saleDate;
		this.totalAmount = totalAmount;
		this.amountPaid = BigDecimal.ZERO;
		this.status = SaleStatus.COMPLETED;
	}

	public BigDecimal getOutstanding() {
		return totalAmount.subtract(amountPaid);
	}

	/** Called by SaleService within the same transaction as the SalePayment write. */
	public void applyPayment(BigDecimal amount) {
		this.amountPaid = this.amountPaid.add(amount);
		this.status = amountPaid.compareTo(totalAmount) >= 0 ? SaleStatus.PAID : SaleStatus.PARTIALLY_PAID;
	}
}
