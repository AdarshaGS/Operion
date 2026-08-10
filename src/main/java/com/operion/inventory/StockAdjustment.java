package com.operion.inventory;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
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
 * A signed correction to an item's balance at a campus - positive for a found-extra
 * during a count, negative for damage/loss. Insert-only, campus-scoped.
 * InventoryService blocks an adjustment that would take the computed balance negative.
 */
@Getter
@Entity
@Table(name = "stock_adjustments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "quantity_delta", nullable = false)
	private int quantityDelta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StockAdjustmentReason reason;

	@Column(name = "adjustment_date", nullable = false)
	private LocalDate adjustmentDate;

	/** Nullable. */
	private String remarks;

	public StockAdjustment(Item item, Campus campus, int quantityDelta, StockAdjustmentReason reason, LocalDate adjustmentDate, String remarks) {
		if (quantityDelta == 0) {
			throw new IllegalArgumentException("Stock adjustment quantity delta cannot be zero");
		}
		this.item = item;
		this.campus = campus;
		this.quantityDelta = quantityDelta;
		this.reason = reason;
		this.adjustmentDate = adjustmentDate;
		this.remarks = remarks;
	}
}
