package com.operion.inventory.api;

import com.operion.inventory.ItemCategory;

public record ItemCategoryResponse(Long id, String code, String name, String description, String status) {

	public static ItemCategoryResponse from(ItemCategory category) {
		return new ItemCategoryResponse(category.getId(), category.getCode(), category.getName(), category.getDescription(),
				category.getStatus().name());
	}
}
