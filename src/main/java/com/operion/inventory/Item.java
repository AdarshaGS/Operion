package com.operion.inventory;

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
import lombok.Setter;

/**
 * Org-wide catalog entry, not campus-scoped - same catalog-vs-ledger split as
 * Book/BookCopy. Stock is tracked per campus via StockEntry/StockIssue/
 * StockAdjustment, not by duplicating the catalog row per campus. unit is free text
 * ("PCS"/"BOX"/"KG"), same convention as GradeLevel.stage - not worth an enum for a
 * value that's just descriptive.
 */
@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_category_id")
	private ItemCategory category;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String unit;

	/** Nullable. */
	private String description;

	/** Nullable - no threshold set by default. */
	@Setter
	@Column(name = "reorder_level")
	private Integer reorderLevel;

	/** Nullable - free text, same convention as unit/description; sub-campus structured
	 * locations are #191's (deferred) scope, not this field's. */
	@Setter
	@Column(name = "storage_location")
	private String storageLocation;

	/** Nullable - the preferred/default vendor for this item, not a requirement to buy
	 * from only this supplier; StockEntry.supplier is recorded per-receipt independently. */
	@Setter
	@ManyToOne
	@JoinColumn(name = "default_supplier_id")
	private Supplier defaultSupplier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ItemStatus status;

	public Item(ItemCategory category, String code, String name, String unit, String description) {
		this.category = category;
		this.code = code;
		this.name = name;
		this.unit = unit;
		this.description = description;
		this.status = ItemStatus.ACTIVE;
	}

	public void discontinue() {
		this.status = ItemStatus.DISCONTINUED;
	}
}
