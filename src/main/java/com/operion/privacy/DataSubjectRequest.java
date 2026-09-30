package com.operion.privacy;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
 * A person's request about their own data. Nothing is fulfilled automatically: staff verify
 * the requester's identity first, then record the outcome. A DELETION request being COMPLETED
 * records that the organisation acted - it does not itself erase anything, because retention
 * duties (fees, audit history) make erasure a deliberate operator decision, not a side effect.
 */
@Getter
@Entity
@Table(name = "data_subject_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSubjectRequest extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Enumerated(EnumType.STRING)
	@Column(name = "request_type", nullable = false, length = 20)
	private DataSubjectRequestType requestType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DataSubjectRequestStatus status;

	/** Nullable. */
	private String description;

	@Column(name = "received_date", nullable = false)
	private LocalDate receivedDate;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	/** Nullable - a User id, no FK by design. */
	@Column(name = "identity_verified_by")
	private Long identityVerifiedBy;

	@Column(name = "identity_verified_at")
	private Instant identityVerifiedAt;

	/** Nullable - how identity was checked, e.g. "Photo ID sighted at front desk". */
	@Column(name = "identity_verification_method")
	private String identityVerificationMethod;

	/** Nullable - a User id, no FK by design. */
	@Column(name = "resolved_by")
	private Long resolvedBy;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	/** Nullable - required on completion or rejection. */
	@Column(name = "resolution_note")
	private String resolutionNote;

	public DataSubjectRequest(Person person, DataSubjectRequestType requestType, String description, LocalDate receivedDate,
			LocalDate dueDate) {
		this.person = person;
		this.requestType = requestType;
		this.description = description;
		this.receivedDate = receivedDate;
		this.dueDate = dueDate;
		this.status = DataSubjectRequestStatus.RECEIVED;
	}

	public void verifyIdentity(String method, Long verifiedBy) {
		requireStatus(DataSubjectRequestStatus.RECEIVED);
		this.status = DataSubjectRequestStatus.IDENTITY_VERIFIED;
		this.identityVerificationMethod = method;
		this.identityVerifiedBy = verifiedBy;
		this.identityVerifiedAt = Instant.now();
	}

	/** Only after identity is verified - nothing is released or acted on for an unverified requester. */
	public void complete(String resolutionNote, Long resolvedBy) {
		requireStatus(DataSubjectRequestStatus.IDENTITY_VERIFIED);
		resolve(DataSubjectRequestStatus.COMPLETED, resolutionNote, resolvedBy);
	}

	public void reject(String resolutionNote, Long resolvedBy) {
		if (status == DataSubjectRequestStatus.COMPLETED || status == DataSubjectRequestStatus.REJECTED) {
			throw new IllegalStateException("Request is already " + status);
		}
		resolve(DataSubjectRequestStatus.REJECTED, resolutionNote, resolvedBy);
	}

	public boolean isOpen() {
		return status == DataSubjectRequestStatus.RECEIVED || status == DataSubjectRequestStatus.IDENTITY_VERIFIED;
	}

	private void resolve(DataSubjectRequestStatus outcome, String resolutionNote, Long resolvedBy) {
		this.status = outcome;
		this.resolutionNote = resolutionNote;
		this.resolvedBy = resolvedBy;
		this.resolvedAt = Instant.now();
	}

	private void requireStatus(DataSubjectRequestStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("Request is " + status + ", expected " + expected);
		}
	}
}
