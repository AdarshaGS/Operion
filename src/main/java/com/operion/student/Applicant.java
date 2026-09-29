package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.academic.GradeLevel;
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
 * A prospective student before admission is decided - deliberately separate from
 * {@link Student}, not a pre-admission StudentStatus, since admissionNumber/admissionDate
 * are NOT NULL there and would be meaningless for an inquiry that never converts. Backed
 * by a Person (same identity table every org member uses) so converting to a Student
 * needs no name/DOB/gender re-entry.
 */
@Getter
@Entity
@Table(name = "applicants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Applicant extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(name = "inquiry_date", nullable = false)
	private LocalDate inquiryDate;

	/** Nullable - free-form, same convention as Student.admissionSource. */
	private String source;

	/** Nullable. */
	private String notes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApplicantStatus status;

	/** Nullable - the grade the guardian is applying for (public inquiry form). */
	@ManyToOne
	@JoinColumn(name = "desired_grade_id")
	private GradeLevel desiredGrade;

	@Column(name = "guardian_name")
	private String guardianName;

	@Column(name = "guardian_phone")
	private String guardianPhone;

	@Column(name = "guardian_email")
	private String guardianEmail;

	@Column(name = "rejection_reason")
	private String rejectionReason;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	public Applicant(Person person, LocalDate inquiryDate, String source, String notes) {
		this.person = person;
		this.inquiryDate = inquiryDate;
		this.source = source;
		this.notes = notes;
		this.status = ApplicantStatus.INQUIRY;
	}

	public void recordInquiryDetails(GradeLevel desiredGrade, String guardianName, String guardianPhone, String guardianEmail) {
		this.desiredGrade = desiredGrade;
		this.guardianName = guardianName;
		this.guardianPhone = guardianPhone;
		this.guardianEmail = guardianEmail;
	}

	public void startReview() {
		if (status != ApplicantStatus.INQUIRY) {
			throw new IllegalStateException("Only an applicant still at inquiry can be moved to review");
		}
		this.status = ApplicantStatus.UNDER_REVIEW;
		this.reviewedAt = Instant.now();
	}

	public void reject(String reason) {
		if (status != ApplicantStatus.INQUIRY && status != ApplicantStatus.UNDER_REVIEW) {
			throw new IllegalStateException("Only an applicant at inquiry or under review can be rejected");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalStateException("A rejection reason is required");
		}
		this.status = ApplicantStatus.REJECTED;
		this.rejectionReason = reason.trim();
		this.reviewedAt = Instant.now();
	}

	public void markConverted() {
		if (status != ApplicantStatus.INQUIRY && status != ApplicantStatus.UNDER_REVIEW) {
			throw new IllegalStateException("Only an applicant at inquiry or under review can be converted");
		}
		this.status = ApplicantStatus.CONVERTED;
	}
}
