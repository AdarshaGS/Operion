package com.operion.hr.api;

import java.time.Instant;

import com.operion.hr.StaffDocument;

public record StaffDocumentResponse(Long id, Long staffProfileId, String documentType, String fileReference, String fileName,
		String mimeType, String verificationStatus, Long verifiedBy, Instant verifiedAt, String status) {

	public static StaffDocumentResponse from(StaffDocument document) {
		return new StaffDocumentResponse(document.getId(), document.getStaffProfile().getId(), document.getDocumentType(),
				document.getFileReference(), document.getFileName(), document.getMimeType(),
				document.getVerificationStatus().name(), document.getVerifiedBy(), document.getVerifiedAt(), document.getStatus().name());
	}
}
