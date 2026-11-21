package com.operion.student;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The year-by-year placement, insert-only across years - THE historical entity that
 * keeps "2025-26 Grade 4 Section B -> 2026-27 Grade 5 Section A" as two rows, never one
 * mutated section_id. is_current is checked unique-per-student in StudentService and backed by
 * the uq_student_enrollments_student_current key over current_marker (1 while current, NULL otherwise -
 * repeated NULLs are allowed by a unique index), same convention as AcademicYear.is_current.
 * Mid-year section reassignment is the one deliberate exception: it mutates section on
 * the current row in place, relying on Foundation's AuditLog for the trail, per
 * ai-context/erp-system-plan.md §2.2.
 */
@Getter
@Entity
@Table(name = "student_enrollments", uniqueConstraints = {
		@UniqueConstraint(name = "uq_student_enrollments_student_year", columnNames = { "student_id", "academic_year_id" }),
		@UniqueConstraint(name = "uq_student_enrollments_section_roll", columnNames = { "section_id", "roll_number" }),
		@UniqueConstraint(name = "uq_student_enrollments_student_current", columnNames = { "student_id", "current_marker" }) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentEnrollment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	/** Nullable - not every school assigns roll numbers immediately. */
	@Column(name = "roll_number")
	private Integer rollNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "enrollment_status", nullable = false, length = 20)
	private StudentEnrollmentStatus enrollmentStatus;

	@Column(name = "is_current", nullable = false)
	private boolean current;

	/** Mirrors {@link #current} as TRUE or NULL so a unique key can allow at most one current enrollment per student. Kept in step by every mutator below. */
	@Column(name = "current_marker")
	private Boolean currentMarker;

	@Column(name = "enrolled_date", nullable = false)
	private LocalDate enrolledDate;

	/** Nullable - null while this enrollment is current. */
	@Column(name = "exit_date")
	private LocalDate exitDate;

	/** Admission-fee standing, written only by finance code in the same transaction as the money movement - see FeeClearanceStatus. */
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_clearance_status", nullable = false, length = 20)
	private FeeClearanceStatus feeClearanceStatus;

	/** Nullable - when feeClearanceStatus last changed. */
	@Column(name = "fee_clearance_changed_at")
	private Instant feeClearanceChangedAt;

	/** Nullable - only set by a manual override (EXEMPTED). */
	@Column(name = "fee_clearance_override_by")
	private Long feeClearanceOverrideBy;

	/** Nullable - the reason recorded with a manual override. */
	@Column(name = "fee_clearance_override_reason", length = 500)
	private String feeClearanceOverrideReason;

	public StudentEnrollment(Student student, AcademicYear academicYear, Section section, Integer rollNumber,
			LocalDate enrolledDate) {
		this.student = student;
		this.academicYear = academicYear;
		this.section = section;
		this.rollNumber = rollNumber;
		this.enrollmentStatus = StudentEnrollmentStatus.ENROLLED;
		this.current = true;
		this.currentMarker = true;
		this.enrolledDate = enrolledDate;
		this.feeClearanceStatus = FeeClearanceStatus.NOT_REQUIRED;
	}

	/** Moves the admission-fee standing and stamps when. Callers (finance code only) decide which transitions are legal. */
	public void changeFeeClearanceStatus(FeeClearanceStatus status) {
		this.feeClearanceStatus = status;
		this.feeClearanceChangedAt = Instant.now();
	}

	/** Manual override: EXEMPTED, with who and why. The time is feeClearanceChangedAt. Callers (finance code only) check the starting status. */
	public void exemptFromFeeClearance(Long overriddenBy, String reason) {
		changeFeeClearanceStatus(FeeClearanceStatus.EXEMPTED);
		this.feeClearanceOverrideBy = overriddenBy;
		this.feeClearanceOverrideReason = reason;
	}

	/** Revokes an override: moves to the recalculated status and forgets who exempted the student and why. */
	public void revokeFeeClearanceOverride(FeeClearanceStatus recalculated) {
		changeFeeClearanceStatus(recalculated);
		this.feeClearanceOverrideBy = null;
		this.feeClearanceOverrideReason = null;
	}

	public void close(StudentEnrollmentStatus exitStatus, LocalDate exitDate) {
		if (!current) {
			throw new IllegalStateException("Enrollment is already closed");
		}
		this.enrollmentStatus = exitStatus;
		this.current = false;
		this.currentMarker = null;
		this.exitDate = exitDate;
	}

	/** Re-enrolment after an exit within the same academic year: the (student, year) pair is unique, so the closed row is revived rather than duplicated. */
	public void reopen(Section section, Integer rollNumber, LocalDate enrolledDate) {
		if (current) {
			throw new IllegalStateException("Enrollment is already current");
		}
		this.section = section;
		this.rollNumber = rollNumber;
		this.enrolledDate = enrolledDate;
		this.enrollmentStatus = StudentEnrollmentStatus.ENROLLED;
		this.current = true;
		this.currentMarker = true;
		this.exitDate = null;
	}

	/**
	 * Mid-year move only - see class-level note. Never used across an academic-year boundary. The roll number is
	 * per section (uq_student_enrollments_section_roll), so the caller supplies the one the student takes in the
	 * new section - the old number may already belong to someone there.
	 */
	public void reassignSection(Section newSection, Integer rollNumber) {
		if (!current) {
			throw new IllegalStateException("Cannot reassign the section of a closed enrollment");
		}
		this.section = newSection;
		this.rollNumber = rollNumber;
	}
}
