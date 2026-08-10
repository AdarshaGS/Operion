package com.operion.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A stock receipt - campus-scoped, insert-only (never edited/deleted, same "reverse
 * with a new entry" principle as Fees' Payment/Refund). Increases the item's balance
 * at that campus, which is computed live from this table plus StockIssue/
 * StockAdjustment - no stored balance column, per the design sign-off.
 */
@Getter
@Entity
@Table(name = "stock_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntry extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@ManyToOne(optional = false)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(nullable = false)
	private int quantity;

	/** Nullable - captured when known, for later valuation reporting. */
	@Column(name = "unit_cost", precision = 10, scale = 2)
	private BigDecimal unitCost;

	@Column(name = "entry_date", nullable = false)
	private LocalDate entryDate;

	/** Nullable - free text, not a vendor entity. */
	private String source;

	/** Nullable. */
	private String remarks;

	public StockEntry(Item item, Campus campus, int quantity, BigDecimal unitCost, LocalDate entryDate, String source, String remarks) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("Stock entry quantity must be positive");
		}
		this.item = item;
		this.campus = campus;
		this.quantity = quantity;
		this.unitCost = unitCost;
		this.entryDate = entryDate;
		this.source = source;
		this.remarks = remarks;
	}
}
