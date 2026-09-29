package com.operion.inventory.api;

import com.operion.inventory.Item;

public record ItemResponse(Long id, Long categoryId, String code, String name, String unit, String description, Integer reorderLevel,
		String storageLocation, Long defaultSupplierId, String defaultSupplierName, String status) {

	public static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getCategory().getId(), item.getCode(), item.getName(), item.getUnit(),
				item.getDescription(), item.getReorderLevel(), item.getStorageLocation(),
				item.getDefaultSupplier() != null ? item.getDefaultSupplier().getId() : null,
				item.getDefaultSupplier() != null ? item.getDefaultSupplier().getName() : null, item.getStatus().name());
	}
}
