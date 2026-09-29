package com.operion.inventory;

public enum StockAdjustmentReason {
	DAMAGE,
	LOSS,
	COUNT_CORRECTION,
	/** Decrement from a PurchaseReturn (com.operion.purchase) - the structured audit trail
	 * back to the originating PO/supplier lives on PurchaseReturn itself, not here. */
	SUPPLIER_RETURN,
	/** Decrement at the source campus of a StockTransfer - the structured audit trail
	 * back to the destination campus lives on StockTransfer itself, not here. Not exposed
	 * in the manual adjustment dialog, set only by InventoryService.recordTransfer. */
	TRANSFER_OUT,
	/** Increment at the destination campus of a StockTransfer - see TRANSFER_OUT. */
	TRANSFER_IN,
	OTHER
}
