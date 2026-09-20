package com.operion.inventory.api;

import com.operion.inventory.Item;

public record ItemExportResponse(Long id, String categoryName, String code, String name, String unit, String description,
		Integer reorderLevel, String status) {

	public static ItemExportResponse from(Item item) {
		return new ItemExportResponse(item.getId(), item.getCategory().getName(), item.getCode(), item.getName(), item.getUnit(),
				item.getDescription(), item.getReorderLevel(), item.getStatus().name());
	}
}
