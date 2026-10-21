package com.operion.document;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The register of official documents the school has issued. dataSnapshot freezes every
 * printed value at first issue, so a reprint shows exactly what the original showed even
 * after a later name or class correction; the PDF itself is never stored, only
 * re-rendered from the snapshot. contentHash is the SHA-256 of that snapshot, for
 * detecting tampering. printCount counts office prints - the second and later carry a
 * DUPLICATE mark. Issuer and issue time are the inherited createdBy/createdAt.
 */
@Getter
@Entity
@Table(name = "issued_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedDocument extends TenantScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "document_kind", nullable = false, length = 30)
	private IssuedDocumentKind documentKind;

	@Column(name = "document_number", nullable = false, length = 60)
	private String documentNumber;

	@Column(name = "subject_type", nullable = false, length = 30)
	private String subjectType;

	@Column(name = "subject_id", nullable = false)
	private Long subjectId;

	@Column(name = "data_snapshot", nullable = false, columnDefinition = "TEXT")
	private String dataSnapshot;

	@Column(name = "content_hash", nullable = false, length = 64)
	private String contentHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IssuedDocumentStatus status;

	@Column(name = "print_count", nullable = false)
	private int printCount;

	@Column(name = "last_printed_at")
	private Instant lastPrintedAt;

	@Column(name = "last_printed_by")
	private Long lastPrintedBy;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancel_reason")
	private String cancelReason;

	public IssuedDocument(IssuedDocumentKind documentKind, String documentNumber, String subjectType, Long subjectId,
			String dataSnapshot, String contentHash) {
		this.documentKind = documentKind;
		this.documentNumber = documentNumber;
		this.subjectType = subjectType;
		this.subjectId = subjectId;
		this.dataSnapshot = dataSnapshot;
		this.contentHash = contentHash;
		this.status = IssuedDocumentStatus.ISSUED;
	}

	public void recordPrint(Long actorId) {
		this.printCount++;
		this.lastPrintedAt = Instant.now();
		this.lastPrintedBy = actorId;
	}

	public void cancel(String reason) {
		if (status == IssuedDocumentStatus.CANCELLED) {
			return;
		}
		this.status = IssuedDocumentStatus.CANCELLED;
		this.cancelledAt = Instant.now();
		this.cancelReason = reason;
	}
}
