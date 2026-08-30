package com.operion.identity;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
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
 * The human/business record (name, DOB, contact, photo) - org-scoped. Role-specific
 * profiles (Student, Teacher, Staff, Guardian) live in other modules and FK to this
 * entity's id, keeping Person itself from becoming a fat cross-module table.
 */
@Getter
@Setter
@Entity
@Table(name = "persons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person extends TenantScopedEntity {

	/** Nullable - a young student may have no login of their own. */
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	private String gender;

	private String phone;

	private String email;

	@Column(name = "photo_url")
	private String photoUrl;

	private String address;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PersonStatus status;

	public Person(String firstName, String lastName) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.status = PersonStatus.ACTIVE;
	}
}
