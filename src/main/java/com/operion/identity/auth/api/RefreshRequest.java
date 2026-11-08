package com.operion.identity.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank @Size(max = 100) String organisationSlug, @NotBlank @Size(max = 512) String refreshToken) {
}
