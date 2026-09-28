package com.operion.transport.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.transport.VehicleDocument;

public record VehicleDocumentResponse(Long id, Long vehicleId, String documentType, String fileReference, String fileName,
		String mimeType, LocalDate expiryDate, String verificationStatus, Long verifiedBy, Instant verifiedAt, String status) {

	public static VehicleDocumentResponse from(VehicleDocument document) {
		return new VehicleDocumentResponse(document.getId(), document.getVehicle().getId(), document.getDocumentType(),
				document.getFileReference(), document.getFileName(), document.getMimeType(), document.getExpiryDate(),
				document.getVerificationStatus().name(), document.getVerifiedBy(), document.getVerifiedAt(), document.getStatus().name());
	}
}
