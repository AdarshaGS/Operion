package com.operion.student.api;

public record UploadDocumentRequest(String documentType, String fileReference, String fileName, String mimeType) {
}
