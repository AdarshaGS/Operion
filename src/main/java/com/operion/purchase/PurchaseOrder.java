package com.operion.purchase;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.inventory.Supplier;
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
 * DRAFT -> SUBMITTED -> APPROVED -> (PARTIALLY_RECEIVED ->) RECEIVED, with CANCELLED
 * reachable from DRAFT/SUBMITTED/APPROVED only - once any goods have been received the
 * order can no longer be cancelled outright (a PurchaseReturn is the right tool then).
 * Lines reference this by FK only (see PurchaseOrderLine) - no owning-side collection
 * here, same convention as StockEntry/StockIssue/StockAdjustment referencing Item/Campus.
 */
@Getter
@Entity
@Table(name = "purchase_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "expected_date", nullable = false)
	private LocalDate expectedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseOrderStatus status;

	public PurchaseOrder(Supplier supplier, Campus campus, LocalDate expectedDate) {
		this.supplier = supplier;
		this.campus = campus;
		this.expectedDate = expectedDate;
		this.status = PurchaseOrderStatus.DRAFT;
	}

	public void submit() {
		if (status != PurchaseOrderStatus.DRAFT) {
			throw new IllegalStateException("Only a draft purchase order can be submitted, was " + status);
		}
		this.status = PurchaseOrderStatus.SUBMITTED;
	}

	public void approve() {
		if (status != PurchaseOrderStatus.SUBMITTED) {
			throw new IllegalStateException("Only a submitted purchase order can be approved, was " + status);
		}
		this.status = PurchaseOrderStatus.APPROVED;
	}

	public void cancel() {
		if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.SUBMITTED && status != PurchaseOrderStatus.APPROVED) {
			throw new IllegalStateException("Cannot cancel a purchase order once receiving has started, was " + status);
		}
		this.status = PurchaseOrderStatus.CANCELLED;
	}

	/** Called by PurchaseOrderService after applying a goods receipt to this order's lines. */
	public void applyReceiptProgress(boolean fullyReceived) {
		if (status != PurchaseOrderStatus.APPROVED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
			throw new IllegalStateException("Only an approved purchase order can receive goods, was " + status);
		}
		this.status = fullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
	}
}
