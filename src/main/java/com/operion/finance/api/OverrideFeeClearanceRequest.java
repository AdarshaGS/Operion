package com.operion.finance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OverrideFeeClearanceRequest(@NotBlank @Size(max = 500) String reason) {
}
