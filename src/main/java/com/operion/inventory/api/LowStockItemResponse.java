package com.operion.inventory.api;

import com.operion.inventory.LowStockItem;

public record LowStockItemResponse(Long id, Long categoryId, String code, String name, String unit, Integer reorderLevel, int balance) {

	public static LowStockItemResponse from(LowStockItem lowStockItem) {
		var item = lowStockItem.item();
		return new LowStockItemResponse(item.getId(), item.getCategory().getId(), item.getCode(), item.getName(), item.getUnit(),
				item.getReorderLevel(), lowStockItem.balance());
	}
}
