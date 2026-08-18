package com.operion.parent.api;

import jakarta.validation.constraints.NotNull;

public record CreateGuardianRequest(@NotNull Long personId, String occupation) {
}
