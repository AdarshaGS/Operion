package com.operion.student.api;

import java.time.Instant;

import com.operion.storage.AssetStorageService;
import com.operion.student.StudentDocument;

public record StudentDocumentResponse(Long id, Long studentId, String documentType, String fileReference, String fileUrl,
		String fileName, String mimeType, String verificationStatus, Long verifiedBy, Instant verifiedAt, String status) {

	static StudentDocumentResponse from(StudentDocument document, AssetStorageService assetStorageService) {
		return new StudentDocumentResponse(document.getId(), document.getStudent().getId(), document.getDocumentType(),
				document.getFileReference(), assetStorageService.resolveUrl(document.getFileReference()), document.getFileName(),
				document.getMimeType(), document.getVerificationStatus().name(), document.getVerifiedBy(), document.getVerifiedAt(),
				document.getStatus().name());
	}
}
