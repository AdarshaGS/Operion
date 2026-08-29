package com.operion.student;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
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
 * Intra-org, cross-campus transfer only - fromCampus/toCampus are both this
 * organisation's own Campus rows. PENDING -> APPROVED/REJECTED, one-way off PENDING,
 * same convention as LeaveRequest. Approving only flips status; it does not itself
 * move the student's active enrollment to a section at the new campus - that stays a
 * manual follow-up via the existing enroll flow, same as LeaveRequest.approve() never
 * cascading into another module.
 */
@Getter
@Entity
@Table(name = "student_transfer_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferRequest extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "from_campus_id")
	private Campus fromCampus;

	@ManyToOne(optional = false)
	@JoinColumn(name = "to_campus_id")
	private Campus toCampus;

	/** Nullable. */
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransferRequestStatus status;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	/** Nullable - unset until approved/rejected. References a User id, no FK by design,
	 * same convention as LeaveRequest.approvedBy. */
	@Column(name = "decided_by")
	private Long decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	public TransferRequest(Student student, Campus fromCampus, Campus toCampus, String reason, Long requestedBy) {
		if (fromCampus.getId().equals(toCampus.getId())) {
			throw new IllegalArgumentException("Transfer request fromCampus and toCampus must differ");
		}
		this.student = student;
		this.fromCampus = fromCampus;
		this.toCampus = toCampus;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.status = TransferRequestStatus.PENDING;
	}

	public void approve(Long decidedBy) {
		requirePending();
		this.status = TransferRequestStatus.APPROVED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	public void reject(Long decidedBy) {
		requirePending();
		this.status = TransferRequestStatus.REJECTED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	private void requirePending() {
		if (status != TransferRequestStatus.PENDING) {
			throw new IllegalStateException("Only a pending transfer request can be decided, was " + status);
		}
	}
}
