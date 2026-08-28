package com.operion.sales;

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

/** One row per (Sale, Item) sold. Unlike PurchaseOrderLine, quantity is fully settled at
 * creation - a sale is a single-step transaction, not received incrementally. */
@Getter
@Entity
@Table(name = "sale_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleLine extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "sale_id")
	private Sale sale;

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	public SaleLine(Sale sale, Item item, int quantity, BigDecimal unitPrice) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Sale line quantity must be positive");
		}
		this.sale = sale;
		this.item = item;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public BigDecimal getLineTotal() {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}
}
