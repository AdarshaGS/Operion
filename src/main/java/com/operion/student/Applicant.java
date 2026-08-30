package com.operion.student;

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

	public Applicant(Person person, LocalDate inquiryDate, String source, String notes) {
		this.person = person;
		this.inquiryDate = inquiryDate;
		this.source = source;
		this.notes = notes;
		this.status = ApplicantStatus.INQUIRY;
	}

	public void reject() {
		if (status != ApplicantStatus.INQUIRY) {
			throw new IllegalStateException("Only an applicant still under inquiry can be rejected");
		}
		this.status = ApplicantStatus.REJECTED;
	}

	public void markConverted() {
		if (status != ApplicantStatus.INQUIRY) {
			throw new IllegalStateException("Only an applicant still under inquiry can be converted");
		}
		this.status = ApplicantStatus.CONVERTED;
	}
}
