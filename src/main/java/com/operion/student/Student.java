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
 * The school-specific extension of a Foundation Person (1:1) - attributes that don't
 * change every year, NOT the year-by-year placement (see StudentEnrollment). Admission
 * details are merged onto this entity rather than a separate StudentAdmission entity
 * for MVP simplicity, per ai-context/erp-system-plan.md §2.2 - revisit as a separate
 * insert-only entity only if re-admission after withdrawal becomes a real requirement.
 */
@Getter
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	/** System-generated (e.g. STU-2026-00042, see StudentIdCounter) - immutable, distinct
	 * from the user-typed {@link #admissionNumber}. */
	@Column(name = "student_id", nullable = false)
	private String studentId;

	@Column(name = "admission_number", nullable = false)
	private String admissionNumber;

	@Column(name = "admission_date", nullable = false)
	private LocalDate admissionDate;

	/** Nullable - e.g. "ONLINE"/"WALK_IN"/"REFERRAL", free-form not enum by design. */
	@Column(name = "admission_source")
	private String admissionSource;

	/** Nullable. */
	@Column(name = "previous_school")
	private String previousSchool;

	/** Nullable - transfer certificate number from the previous school. */
	@Column(name = "tc_number")
	private String tcNumber;

	/** Nullable. */
	@Column(name = "entrance_score")
	private Double entranceScore;

	/** Nullable. */
	@Column(name = "blood_group")
	private String bloodGroup;

	/** Nullable - RTE/reservation category. Access-controlled once permission enforcement exists. */
	private String category;

	/** Nullable. */
	private String nationality;

	/** Nullable. */
	private String remarks;

	/** Nullable free-text - allergies, conditions, medication. Same access-control note as {@link #category}. */
	@Column(name = "medical_alerts")
	private String medicalAlerts;

	/** Nullable - a contact who isn't necessarily one of the student's linked guardians (see StudentGuardian). */
	@Column(name = "emergency_contact_name")
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone")
	private String emergencyContactPhone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudentStatus status;

	public Student(Person person, String studentId, String admissionNumber, LocalDate admissionDate,
			String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
			String category, String nationality, String remarks, String medicalAlerts, String emergencyContactName,
			String emergencyContactPhone) {
		this.person = person;
		this.studentId = studentId;
		this.admissionNumber = admissionNumber;
		this.admissionDate = admissionDate;
		this.admissionSource = admissionSource;
		this.previousSchool = previousSchool;
		this.tcNumber = tcNumber;
		this.entranceScore = entranceScore;
		this.bloodGroup = bloodGroup;
		this.category = category;
		this.nationality = nationality;
		this.remarks = remarks;
		this.medicalAlerts = medicalAlerts;
		this.emergencyContactName = emergencyContactName;
		this.emergencyContactPhone = emergencyContactPhone;
		this.status = StudentStatus.ADMITTED;
	}

	public void activate() {
		if (status != StudentStatus.ADMITTED) {
			throw new IllegalStateException("Only an admitted student can be activated");
		}
		this.status = StudentStatus.ACTIVE;
	}

	public void exit(StudentStatus exitStatus) {
		if (status != StudentStatus.ACTIVE) {
			throw new IllegalStateException("Only an active student can exit");
		}
		if (exitStatus != StudentStatus.TRANSFERRED_OUT && exitStatus != StudentStatus.GRADUATED
				&& exitStatus != StudentStatus.WITHDRAWN) {
			throw new IllegalArgumentException("Exit status must be TRANSFERRED_OUT, GRADUATED, or WITHDRAWN");
		}
		this.status = exitStatus;
	}

	public void markAlumni() {
		if (status != StudentStatus.GRADUATED) {
			throw new IllegalStateException("Only a graduated student can become alumni");
		}
		this.status = StudentStatus.ALUMNI;
	}
}
