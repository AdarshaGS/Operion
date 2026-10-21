package com.operion.certificate;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.document.IssuedDocument;
import com.operion.student.Student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One certificate for one student, from request to issue. Requester is the inherited createdBy. Once issued,
 * the printed content lives in the linked {@link IssuedDocument} (frozen snapshot); this row keeps the
 * workflow trail: who approved, who overrode dues clearance and why, and the public verification token.
 */
@Getter
@Entity
@Table(name = "certificate_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateRequest extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "certificate_type_id")
	private CertificateType certificateType;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CertificateRequestStatus status;

	private String purpose;

	/** JSON object of manual field key -> typed value. */
	@Column(name = "manual_values", columnDefinition = "TEXT")
	private String manualValues;

	@Enumerated(EnumType.STRING)
	@Column(name = "requested_via", nullable = false, length = 10)
	private CertificateRequestChannel requestedVia;

	@Column(name = "approved_by")
	private Long approvedBy;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "rejected_by")
	private Long rejectedBy;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "clearance_override_reason", length = 500)
	private String clearanceOverrideReason;

	@Column(name = "clearance_overridden_by")
	private Long clearanceOverriddenBy;

	@OneToOne
	@JoinColumn(name = "issued_document_id")
	private IssuedDocument issuedDocument;

	@Column(name = "certificate_number", length = 60)
	private String certificateNumber;

	@Column(name = "verification_token", length = 40)
	private String verificationToken;

	@Column(name = "issued_at")
	private Instant issuedAt;

	@Column(name = "issued_by")
	private Long issuedBy;

	public CertificateRequest(CertificateType certificateType, Student student, String purpose, String manualValues,
			CertificateRequestChannel requestedVia) {
		this.certificateType = certificateType;
		this.student = student;
		this.purpose = purpose;
		this.manualValues = manualValues;
		this.requestedVia = requestedVia;
		this.status = certificateType.isApprovalRequired() ? CertificateRequestStatus.PENDING_APPROVAL : CertificateRequestStatus.APPROVED;
	}

	public void updateManualValues(String purpose, String manualValues) {
		requireStatus(CertificateRequestStatus.PENDING_APPROVAL, CertificateRequestStatus.APPROVED);
		this.purpose = purpose;
		this.manualValues = manualValues;
	}

	public void approve(Long actorId) {
		requireStatus(CertificateRequestStatus.PENDING_APPROVAL);
		this.status = CertificateRequestStatus.APPROVED;
		this.approvedBy = actorId;
		this.approvedAt = Instant.now();
	}

	public void reject(Long actorId, String reason) {
		requireStatus(CertificateRequestStatus.PENDING_APPROVAL, CertificateRequestStatus.APPROVED);
		this.status = CertificateRequestStatus.REJECTED;
		this.rejectedBy = actorId;
		this.rejectionReason = reason;
	}

	public void overrideClearance(Long actorId, String reason) {
		this.clearanceOverriddenBy = actorId;
		this.clearanceOverrideReason = reason;
	}

	public void markIssued(IssuedDocument document, String token, Long actorId) {
		requireStatus(CertificateRequestStatus.APPROVED);
		this.status = CertificateRequestStatus.ISSUED;
		this.issuedDocument = document;
		this.certificateNumber = document.getDocumentNumber();
		this.verificationToken = token;
		this.issuedAt = Instant.now();
		this.issuedBy = actorId;
	}

	public void cancel(String reason) {
		requireStatus(CertificateRequestStatus.ISSUED);
		this.status = CertificateRequestStatus.CANCELLED;
		issuedDocument.cancel(reason);
	}

	private void requireStatus(CertificateRequestStatus... allowed) {
		for (CertificateRequestStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new IllegalStateException("Certificate request " + getId() + " is " + status + ", which does not allow this action");
	}
}
