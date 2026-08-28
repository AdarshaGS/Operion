package com.operion.purchase;

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
 * Insert-only audit trail for stock returned to a supplier against a received Purchase
 * Order line - the balance decrement itself is a StockAdjustment(SUPPLIER_RETURN) written
 * alongside this row by PurchaseOrderService.recordReturn (reusing InventoryService's
 * negative-balance guard), not derived from this table. This table exists purely so a
 * return can be traced back to its PO/supplier, which a generic StockAdjustment row can't.
 */
@Getter
@Entity
@Table(name = "purchase_returns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseReturn extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "purchase_order_line_id")
	private PurchaseOrderLine purchaseOrderLine;

	@Column(nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseReturnReason reason;

	@Column(name = "return_date", nullable = false)
	private LocalDate returnDate;

	/** Nullable. */
	private String remarks;

	public PurchaseReturn(PurchaseOrderLine purchaseOrderLine, int quantity, PurchaseReturnReason reason, LocalDate returnDate, String remarks) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Purchase return quantity must be positive");
		}
		this.purchaseOrderLine = purchaseOrderLine;
		this.quantity = quantity;
		this.reason = reason;
		this.returnDate = returnDate;
		this.remarks = remarks;
	}
}
