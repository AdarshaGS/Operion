package com.operion.hr;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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
 * The staff-specific extension of Person - FKs directly to Person, not through
 * OrganisationMembership, same "role-specific profile FKs to Person" pattern as
 * Student/Guardian. campus is nullable - org-wide staff (e.g. IT admin across
 * campuses) vs. single-campus teachers, same nullability convention as
 * OrganisationMembership.campus. status deliberately excludes an ON_LEAVE value -
 * that's a day-level attendance concept (still not wired, per the class doc on
 * LeaveRequest), not a master-record status.
 */
@Getter
@Entity
@Table(name = "staff_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffProfile extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	/** Nullable - null means org-wide, not a single campus. */
	@ManyToOne
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "employee_code", nullable = false)
	private String employeeCode;

	@Column(nullable = false)
	private String designation;

	/** Nullable. */
	private String department;

	@Column(name = "date_of_joining", nullable = false)
	private LocalDate dateOfJoining;

	@Enumerated(EnumType.STRING)
	@Column(name = "employment_type", nullable = false, length = 20)
	private EmploymentType employmentType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StaffProfileStatus status;

	public StaffProfile(Person person, Campus campus, String employeeCode, String designation, String department,
			LocalDate dateOfJoining, EmploymentType employmentType) {
		this.person = person;
		this.campus = campus;
		this.employeeCode = employeeCode;
		this.designation = designation;
		this.department = department;
		this.dateOfJoining = dateOfJoining;
		this.employmentType = employmentType;
		this.status = StaffProfileStatus.ACTIVE;
	}

	public void changeStatus(StaffProfileStatus status) {
		this.status = status;
	}
}
