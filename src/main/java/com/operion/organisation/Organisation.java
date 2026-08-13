package com.operion.organisation;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The tenant root. Every other organisation-scoped entity carries a foreign key back
 * to this row's id (see {@link com.operion.common.TenantScopedEntity}) - Organisation
 * itself has no organisation_id column, it defines the scope.
 */
@Getter
@Entity
@Table(name = "organisations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organisation extends BaseEntity {

	@Setter
	@Column(nullable = false)
	private String name;

	@Setter
	@Column(name = "legal_name")
	private String legalName;

	@Column(nullable = false, unique = true)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrganisationStatus status;

	public Organisation(String name, String legalName, String slug) {
		this.name = name;
		this.legalName = legalName;
		this.slug = slug;
		this.status = OrganisationStatus.TRIAL;
	}

	/** Only path by which status changes - no restricted transition map, any status can move to any other. */
	public void changeStatus(OrganisationStatus target) {
		this.status = target;
	}
}
