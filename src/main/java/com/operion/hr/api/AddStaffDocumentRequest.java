package com.operion.hr.api;

import java.time.LocalDate;

/** expiryDate is nullable - not every document type expires. */
public record AddStaffDocumentRequest(String documentType, String fileReference, String fileName, String mimeType, LocalDate expiryDate) {
}
