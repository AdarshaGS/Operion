package com.operion.hr.api;

public record AddStaffDocumentRequest(String documentType, String fileReference, String fileName, String mimeType) {
}
