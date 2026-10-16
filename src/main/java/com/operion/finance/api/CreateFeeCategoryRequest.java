package com.operion.finance.api;

import com.operion.finance.FeeCategoryType;
import jakarta.validation.constraints.NotBlank;

public record CreateFeeCategoryRequest(@NotBlank String code, @NotBlank String name, String description, FeeCategoryType categoryType) {
}
