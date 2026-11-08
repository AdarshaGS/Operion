package com.operion.finance.api;

import com.operion.finance.FeeCategoryType;
import jakarta.validation.constraints.NotBlank;

/** {@code requiredAtAdmission} is optional and defaults to false when omitted. */
public record CreateFeeCategoryRequest(@NotBlank String code, @NotBlank String name, String description, FeeCategoryType categoryType,
		Boolean requiredAtAdmission) {
}
