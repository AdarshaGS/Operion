package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

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
 * A prospective student before an admission decision is made - deliberately not a
 * Student row: admissionNumber/admissionDate are NOT NULL there and meaningless for a
 * mere inquiry (#114). Insert-only decision trail, same convention as JobApplication -
 * approving here does not itself create the Student; admissions staff still run the
 * normal admit flow afterward, same as an approved JobApplication still needs a manual
 * Staff creation.
 */
@Getter
@Entity
@Table(name = "student_applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentApplication extends TenantScopedEntity {

	@Column(name = "applicant_name", nullable = false)
	private String applicantName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	/** Nullable. */
	private String gender;

	/** Nullable. */
	@Column(name = "guardian_name")
	private String guardianName;

	/** Nullable. */
	@Column(name = "guardian_phone")
	private String guardianPhone;

	/** Nullable - DB-level FK to grade_levels (see migration), but no JPA @ManyToOne here;
	 * a raw id is all this lightweight entity needs. */
	@Column(name = "desired_grade_level_id")
	private Long desiredGradeLevelId;

	/** Nullable. */
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudentApplicationStatus status;

	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt;

	/** Nullable - unset until approved/rejected. References a User id, no FK by design,
	 * same convention as JobApplication.decidedBy. */
	@Column(name = "decided_by")
	private Long decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	public StudentApplication(String applicantName, LocalDate dateOfBirth, String gender, String guardianName,
			String guardianPhone, Long desiredGradeLevelId, String notes) {
		this.applicantName = applicantName;
		this.dateOfBirth = dateOfBirth;
		this.gender = gender;
		this.guardianName = guardianName;
		this.guardianPhone = guardianPhone;
		this.desiredGradeLevelId = desiredGradeLevelId;
		this.notes = notes;
		this.status = StudentApplicationStatus.PENDING;
		this.appliedAt = Instant.now();
	}

	public void approve(Long decidedBy) {
		requirePending();
		this.status = StudentApplicationStatus.APPROVED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	public void reject(Long decidedBy) {
		requirePending();
		this.status = StudentApplicationStatus.REJECTED;
		this.decidedBy = decidedBy;
		this.decidedAt = Instant.now();
	}

	private void requirePending() {
		if (status != StudentApplicationStatus.PENDING) {
			throw new IllegalStateException("Only a pending application can be decided, was " + status);
		}
	}
}
