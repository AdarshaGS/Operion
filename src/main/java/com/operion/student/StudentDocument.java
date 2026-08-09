package com.operion.student;

import java.time.Instant;

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
 * Re-upload = a new row, with the old row superseded - never overwrite fileReference in
 * place, so the file history survives an inspection/compliance review, per
 * ai-context/erp-system-plan.md §2.2. The DB never stores file bytes - fileReference is
 * metadata pointing at wherever the file actually lives.
 */
@Getter
@Entity
@Table(name = "student_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentDocument extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@Column(name = "document_type", nullable = false)
	private String documentType;

	@Column(name = "file_reference", nullable = false)
	private String fileReference;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "mime_type")
	private String mimeType;

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
	private StudentDocumentStatus status;

	public StudentDocument(Student student, String documentType, String fileReference, String fileName,
			String mimeType) {
		this.student = student;
		this.documentType = documentType;
		this.fileReference = fileReference;
		this.fileName = fileName;
		this.mimeType = mimeType;
		this.verificationStatus = DocumentVerificationStatus.PENDING;
		this.status = StudentDocumentStatus.ACTIVE;
	}

	public void verify(DocumentVerificationStatus verificationStatus, Long verifiedBy, Instant verifiedAt) {
		this.verificationStatus = verificationStatus;
		this.verifiedBy = verifiedBy;
		this.verifiedAt = verifiedAt;
	}

	public void supersede() {
		if (status == StudentDocumentStatus.SUPERSEDED) {
			throw new IllegalStateException("Document is already superseded");
		}
		this.status = StudentDocumentStatus.SUPERSEDED;
	}
}
