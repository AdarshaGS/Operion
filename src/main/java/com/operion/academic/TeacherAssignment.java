package com.operion.academic;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
 * Unifies subject-teacher and homeroom-teacher (subject == null) assignment as one
 * insert-only history: a mid-year teacher change ends this row and a new row is
 * inserted, never an in-place update of teacherPerson. academicYear is denormalized
 * off section->schoolClass->academicYear - avoids a 3-way join on "assignments for
 * year X" reports, per ai-context/erp-system-plan.md §2.1.
 */
@Getter
@Entity
@Table(name = "teacher_assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherAssignment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	/** Nullable - null means this is the HOMEROOM row for the section. */
	@ManyToOne
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_person_id")
	private Person teacherPerson;

	@Enumerated(EnumType.STRING)
	@Column(name = "assignment_type", nullable = false, length = 20)
	private TeacherAssignmentType assignmentType;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Nullable - null means ongoing. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TeacherAssignmentStatus status;

	public TeacherAssignment(AcademicYear academicYear, Section section, Subject subject, Person teacherPerson,
			TeacherAssignmentType assignmentType, LocalDate startDate) {
		this.academicYear = academicYear;
		this.section = section;
		this.subject = subject;
		this.teacherPerson = teacherPerson;
		this.assignmentType = assignmentType;
		this.startDate = startDate;
		this.status = TeacherAssignmentStatus.ACTIVE;
	}

	public void end(LocalDate endDate) {
		if (status == TeacherAssignmentStatus.ENDED) {
			throw new IllegalStateException("Teacher assignment is already ended");
		}
		this.endDate = endDate;
		this.status = TeacherAssignmentStatus.ENDED;
	}
}
