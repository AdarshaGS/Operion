package com.operion.transport.api;

import java.time.LocalDate;

/** expiryDate is nullable - not every document type expires. */
public record AddVehicleDocumentRequest(String documentType, String fileReference, String fileName, String mimeType, LocalDate expiryDate) {
}
