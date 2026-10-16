package com.operion.document.api;

import com.operion.document.TemplateStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UpsertDocumentTemplateRequest(@NotNull TemplateStyle templateStyle, @NotBlank @Pattern(regexp = "A4|Letter", message = "pageSize must be one of A4, Letter") String pageSize, @NotBlank String fontStyle,
		@Positive int fontSize, String headerSubtext) {
}
