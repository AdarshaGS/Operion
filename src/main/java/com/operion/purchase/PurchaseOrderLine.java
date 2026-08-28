package com.operion.purchase;

import java.math.BigDecimal;

import com.operion.common.TenantScopedEntity;
import com.operion.inventory.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per (PurchaseOrder, Item) requested. quantityReceived accumulates across one or
 * more goods receipts (partial delivery is normal) and is what PurchaseOrderService checks
 * against when deciding PARTIALLY_RECEIVED vs RECEIVED - never decremented on its own; a
 * PurchaseReturn against over-received stock is a separate ledger entry, not a rollback of
 * this counter.
 */
@Getter
@Entity
@Table(name = "purchase_order_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "purchase_order_id")
	private PurchaseOrder purchaseOrder;

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitCost;

	@Column(name = "quantity_received", nullable = false)
	private int quantityReceived;

	public PurchaseOrderLine(PurchaseOrder purchaseOrder, Item item, int quantity, BigDecimal unitCost) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Purchase order line quantity must be positive");
		}
		this.purchaseOrder = purchaseOrder;
		this.item = item;
		this.quantity = quantity;
		this.unitCost = unitCost;
		this.quantityReceived = 0;
	}

	public void receive(int quantityThisReceipt) {
		if (quantityThisReceipt <= 0) {
			throw new IllegalArgumentException("Received quantity must be positive");
		}
		if (quantityReceived + quantityThisReceipt > quantity) {
			throw new IllegalStateException(
					"Cannot receive " + quantityThisReceipt + " on line " + getId() + " - only " + (quantity - quantityReceived) + " left to receive");
		}
		this.quantityReceived += quantityThisReceipt;
	}

	public boolean isFullyReceived() {
		return quantityReceived >= quantity;
	}
}
