package com.operion.finance.api;

import com.operion.finance.FeeCategoryType;

public record CreateFeeCategoryRequest(String code, String name, String description, FeeCategoryType categoryType) {
}
