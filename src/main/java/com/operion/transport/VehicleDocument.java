package com.operion.transport;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Insurance/permit/fitness/pollution certificates and their expiry, per #156. Re-upload
 * = a new row, with the old row superseded - never overwrite fileReference in place,
 * same pattern as StaffDocument/StudentDocument. The DB never stores file bytes -
 * fileReference is metadata pointing at wherever the file actually lives. documentType
 * is a free string, not an enum, same precedent as StaffDocument - the certificate set
 * varies by vehicle type/jurisdiction and isn't this codebase's to fix.
 */
@Getter
@Entity
@Table(name = "vehicle_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleDocument extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "vehicle_id")
	private Vehicle vehicle;

	@Column(name = "document_type", nullable = false)
	private String documentType;

	@Column(name = "file_reference", nullable = false)
	private String fileReference;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "mime_type")
	private String mimeType;

	/** Nullable - not every document type expires. */
	@Column(name = "expiry_date")
	private LocalDate expiryDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", nullable = false, length = 20)
	private DocumentVerificationStatus verificationStatus;

	/** Nullable - unset until verified/rejected. References a User id, no FK by design. */
	@Column(name = "verified_by")
	private Long verifiedBy;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleDocumentStatus status;

	public VehicleDocument(Vehicle vehicle, String documentType, String fileReference, String fileName, String mimeType,
			LocalDate expiryDate) {
		this.vehicle = vehicle;
		this.documentType = documentType;
		this.fileReference = fileReference;
		this.fileName = fileName;
		this.mimeType = mimeType;
		this.expiryDate = expiryDate;
		this.verificationStatus = DocumentVerificationStatus.PENDING;
		this.status = VehicleDocumentStatus.ACTIVE;
	}

	public void verify(DocumentVerificationStatus verificationStatus, Long verifiedBy, Instant verifiedAt) {
		this.verificationStatus = verificationStatus;
		this.verifiedBy = verifiedBy;
		this.verifiedAt = verifiedAt;
	}

	public void supersede() {
		if (status == VehicleDocumentStatus.SUPERSEDED) {
			throw new IllegalStateException("Document is already superseded");
		}
		this.status = VehicleDocumentStatus.SUPERSEDED;
	}
}
