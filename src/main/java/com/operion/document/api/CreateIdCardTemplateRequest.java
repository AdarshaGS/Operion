package com.operion.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateIdCardTemplateRequest(@NotBlank String name, @Positive double widthMm, @Positive double heightMm,
		@NotBlank String layoutJson) {
}
