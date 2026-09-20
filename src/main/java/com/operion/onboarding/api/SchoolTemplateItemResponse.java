package com.operion.onboarding.api;

import java.util.Set;

import com.operion.onboarding.SchoolTemplateItem;

public record SchoolTemplateItemResponse(Long id, String category, String name, String code, String description,
		Integer sequenceOrder, String stage, String categoryType, Double minPercentage, String remark, Set<String> permissionCodes) {

	public static SchoolTemplateItemResponse from(SchoolTemplateItem item) {
		return new SchoolTemplateItemResponse(item.getId(), item.getCategory().name(), item.getName(), item.getCode(), item.getDescription(),
				item.getSequenceOrder(), item.getStage(), item.getCategoryType(), item.getMinPercentage(), item.getRemark(),
				item.permissionCodeSet());
	}
}
