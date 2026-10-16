package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;


public record UploadDocumentRequest(@NotBlank String documentType, @NotBlank String fileReference,
		@NotBlank String fileName, String mimeType) {
}
