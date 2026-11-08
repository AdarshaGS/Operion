package com.operion.finance.api;

import jakarta.validation.constraints.NotNull;

public record UpdateFeeCategoryAdmissionRequest(@NotNull Boolean requiredAtAdmission) {
}
