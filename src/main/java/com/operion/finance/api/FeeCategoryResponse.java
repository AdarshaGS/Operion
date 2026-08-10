package com.operion.finance.api;

import com.operion.finance.FeeCategory;

public record FeeCategoryResponse(Long id, String code, String name, String description, String status) {

	static FeeCategoryResponse from(FeeCategory category) {
		return new FeeCategoryResponse(
				category.getId(), category.getCode(), category.getName(), category.getDescription(), category.getStatus().name());
	}
}
