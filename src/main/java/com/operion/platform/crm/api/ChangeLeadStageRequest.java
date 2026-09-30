package com.operion.platform.crm.api;

import jakarta.validation.constraints.NotBlank;

public record ChangeLeadStageRequest(@NotBlank String stage) {
}
