package com.operion.hr;

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
 * Insert-only - the decision trail is just the status history, no separate audit
 * table, same convention as LeaveRequest. specialization is deliberately free text
 * (not a school-specific "subjects taught" field) so this pipeline works for any role
 * an org is hiring for, not just teachers - see ai-context's multi-industry direction.
 */
@Getter
@Entity
@Table(name = "job_applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication extends TenantScopedEntity {

	@Column(name = "applicant_name", nullable = false)
	private String applicantName;

	@Column(nullable = false)
	private String email;

	/** Nullable - free text, e.g. "Mathematics, Physics" or "Payroll, Compliance". */
	private String specialization;

	@Column(name = "years_experience")
	private Integer yearsExperience;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobApplicationStatus status;

	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt;

	/** Nullable - unset until approved/rejected. References a User id, no FK by design,
	 * same convention as LeaveRequest.approvedBy. */
	@Column(name = "decided_by")
	private Long decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	public JobApplication(String applicantName, String email, String specialization, Integer yearsExperience) {
		this.applicantName = applicantName;
		this.email = email;
		this.specialization = specialization;
		this.yearsExperience = yearsExperience;
		this.status = JobApplicationStatus.PENDING;
		this.appliedAt = Instant.now();
	}

	public void approve(Long decidedBy) {
		requirePending();
		this.status = JobApplicationStatus.APPROVED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	public void reject(Long decidedBy) {
		requirePending();
		this.status = JobApplicationStatus.REJECTED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	private void requirePending() {
		if (status != JobApplicationStatus.PENDING) {
			throw new IllegalStateException("Only a pending job application can be decided, was " + status);
		}
	}
}
