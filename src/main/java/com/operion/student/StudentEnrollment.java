package com.operion.student;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The year-by-year placement, insert-only across years - THE historical entity that
 * keeps "2025-26 Grade 4 Section B -> 2026-27 Grade 5 Section A" as two rows, never one
 * mutated section_id. is_current is enforced unique-per-student in StudentService,
 * same convention as AcademicYear.is_current / TeacherAssignment's ACTIVE-row rule.
 * Mid-year section reassignment is the one deliberate exception: it mutates section on
 * the current row in place, relying on Foundation's AuditLog for the trail, per
 * ai-context/erp-system-plan.md §2.2.
 */
@Getter
@Entity
@Table(name = "student_enrollments")
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

	@Column(name = "enrolled_date", nullable = false)
	private LocalDate enrolledDate;

	/** Nullable - null while this enrollment is current. */
	@Column(name = "exit_date")
	private LocalDate exitDate;

	public StudentEnrollment(Student student, AcademicYear academicYear, Section section, Integer rollNumber,
			LocalDate enrolledDate) {
		this.student = student;
		this.academicYear = academicYear;
		this.section = section;
		this.rollNumber = rollNumber;
		this.enrollmentStatus = StudentEnrollmentStatus.ENROLLED;
		this.current = true;
		this.enrolledDate = enrolledDate;
	}

	public void close(StudentEnrollmentStatus exitStatus, LocalDate exitDate) {
		if (!current) {
			throw new IllegalStateException("Enrollment is already closed");
		}
		this.enrollmentStatus = exitStatus;
		this.current = false;
		this.exitDate = exitDate;
	}

	/** Mid-year move only - see class-level note. Never used across an academic-year boundary. */
	public void reassignSection(Section newSection) {
		if (!current) {
			throw new IllegalStateException("Cannot reassign the section of a closed enrollment");
		}
		this.section = newSection;
	}
}
