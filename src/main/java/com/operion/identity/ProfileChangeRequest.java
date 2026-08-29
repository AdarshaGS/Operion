package com.operion.identity;

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
 * A small fixed set of allowed fields (phone/email/photoUrl) rather than a generic
 * diff/key-value table, deliberately - see GitHub #36. Only the fields the requester
 * actually wants changed are non-null; approve() applies just those onto Person, never
 * clobbering the others. Works for any role (staff, guardian, etc.) since it targets
 * the caller's own Person, not a guardian-specific concept.
 */
@Getter
@Entity
@Table(name = "profile_change_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileChangeRequest extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	/** At least one of phone/email/photoUrl is non-null - enforced in the constructor. */
	private String phone;

	private String email;

	@Column(name = "photo_url")
	private String photoUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProfileChangeRequestStatus status;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	/** Nullable - unset until approved/rejected. References a User id, no FK by design,
	 * same convention as LeaveRequest.approvedBy. */
	@Column(name = "reviewed_by")
	private Long reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	public ProfileChangeRequest(Person person, String phone, String email, String photoUrl, Long requestedBy) {
		if (phone == null && email == null && photoUrl == null) {
			throw new IllegalArgumentException("A profile change request must change at least one of phone, email, or photoUrl");
		}
		this.person = person;
		this.phone = phone;
		this.email = email;
		this.photoUrl = photoUrl;
		this.requestedBy = requestedBy;
		this.status = ProfileChangeRequestStatus.PENDING;
	}

	/** Applies only the non-null requested fields onto the target Person. */
	public void approve(Long reviewedBy) {
		requirePending();
		if (phone != null) {
			person.setPhone(phone);
		}
		if (email != null) {
			person.setEmail(email);
		}
		if (photoUrl != null) {
			person.setPhotoUrl(photoUrl);
		}
		this.status = ProfileChangeRequestStatus.APPROVED;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = Instant.now();
	}

	public void reject(Long reviewedBy) {
		requirePending();
		this.status = ProfileChangeRequestStatus.REJECTED;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = Instant.now();
	}

	private void requirePending() {
		if (status != ProfileChangeRequestStatus.PENDING) {
			throw new IllegalStateException("Only a pending profile change request can be decided, was " + status);
		}
	}
}
