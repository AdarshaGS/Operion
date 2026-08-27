package com.operion.authorization;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.identity.User;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
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
import lombok.Setter;

/**
 * Answers "is this user part of this org, as which role, acting as which person,
 * optionally scoped to which campus." A user holds one row per role - simultaneous
 * student/parent/teacher relationships are multiple membership rows, not a
 * multi-valued role column, per ai-context/erp-system-plan.md §1.1.
 */
@Getter
@Entity
@Table(name = "organisation_memberships")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationMembership extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@ManyToOne(optional = false)
	@JoinColumn(name = "role_id")
	private Role role;

	/** Nullable - null means org-wide access rather than a single campus. */
	@ManyToOne
	@JoinColumn(name = "campus_id")
	private Campus campus;

	/** Nullable - not every member needs a department (e.g. an externally-managed
	 * accountant with a Role but no HR profile), same nullability convention as campus. */
	@ManyToOne
	@JoinColumn(name = "department_id")
	private Department department;

	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MembershipStatus status;

	public OrganisationMembership(User user, Person person, Role role, Campus campus) {
		this(user, person, role, campus, null);
	}

	public OrganisationMembership(User user, Person person, Role role, Campus campus, Department department) {
		this.user = user;
		this.person = person;
		this.role = role;
		this.campus = campus;
		this.department = department;
		this.status = MembershipStatus.ACTIVE;
	}
}
