package com.operion.parent;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A thin extension of Person (1:1) - reuses name/phone/email/address from Foundation
 * rather than duplicating contact data. Created lazily the first time a Person is
 * linked to a student as a guardian, per ai-context/erp-system-plan.md §2.3.
 */
@Getter
@Entity
@Table(name = "guardians")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	/** Nullable. */
	private String occupation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GuardianStatus status;

	public Guardian(Person person, String occupation) {
		this.person = person;
		this.occupation = occupation;
		this.status = GuardianStatus.ACTIVE;
	}
}
