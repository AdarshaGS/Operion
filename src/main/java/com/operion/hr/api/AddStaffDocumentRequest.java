package com.operion.hr.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** expiryDate is nullable - not every document type expires. Lengths match the staff_documents columns. */
public record AddStaffDocumentRequest(@Size(max = 100) String documentType, @Size(max = 500) String fileReference,
		@Size(max = 255) String fileName, @Size(max = 100) String mimeType, LocalDate expiryDate) {
}
