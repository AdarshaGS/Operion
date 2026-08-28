package com.operion.inventory;

public enum StockAdjustmentReason {
	DAMAGE,
	LOSS,
	COUNT_CORRECTION,
	/** Decrement from a PurchaseReturn (com.operion.purchase) - the structured audit trail
	 * back to the originating PO/supplier lives on PurchaseReturn itself, not here. */
	SUPPLIER_RETURN,
	OTHER
}
