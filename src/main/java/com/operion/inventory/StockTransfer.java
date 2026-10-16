package com.operion.inventory;

import com.operion.common.InvalidRequestException;
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
 * The structured audit trail for a stock transfer between two campuses - insert-only,
 * same shape as PurchaseReturn's relationship to StockAdjustment (#175). The actual
 * balance effect is two StockAdjustment rows (TRANSFER_OUT at fromCampus, TRANSFER_IN at
 * toCampus) written alongside this by InventoryService.recordTransfer, reusing the
 * existing negative-balance guard rather than a fourth balance-affecting ledger table
 * that getBalance/getLowStockItems would need to know about.
 */
@Getter
@Entity
@Table(name = "stock_transfers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransfer extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "item_id")
	private Item item;

	@ManyToOne(optional = false)
	@JoinColumn(name = "from_campus_id")
	private Campus fromCampus;

	@ManyToOne(optional = false)
	@JoinColumn(name = "to_campus_id")
	private Campus toCampus;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "transfer_date", nullable = false)
	private LocalDate transferDate;

	/** Nullable. */
	private String remarks;

	public StockTransfer(Item item, Campus fromCampus, Campus toCampus, int quantity, LocalDate transferDate, String remarks) {
		if (quantity <= 0) {
			throw new InvalidRequestException("Stock transfer quantity must be positive");
		}
		if (fromCampus.getId().equals(toCampus.getId())) {
			throw new InvalidRequestException("Cannot transfer stock to the same campus");
		}
		this.item = item;
		this.fromCampus = fromCampus;
		this.toCampus = toCampus;
		this.quantity = quantity;
		this.transferDate = transferDate;
		this.remarks = remarks;
	}
}
