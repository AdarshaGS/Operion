package com.operion.integration;

import com.operion.common.BaseEntity;
import com.operion.organisation.Organisation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Whether an organisation is entitled to configure a given {@link ExternalService} -
 * a platform admin's toggle, not the organisation's own. Plain {@code organisation_id} FK
 * (extends BaseEntity, not TenantScopedEntity) rather than a Hibernate {@code @TenantId} -
 * a platform admin must see and flip this across every organisation, the same "visible
 * across every org" reasoning {@code com.operion.billing.Subscription} documents. No row
 * for an (organisation, service) pair means not enabled - rows are created on first
 * toggle, not pre-seeded.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_external_services")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationExternalService extends BaseEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "organisation_id")
	private Organisation organisation;

	@ManyToOne(optional = false)
	@JoinColumn(name = "external_service_id")
	private ExternalService externalService;

	@Column(nullable = false)
	private boolean enabled;

	public OrganisationExternalService(Organisation organisation, ExternalService externalService, boolean enabled) {
		this.organisation = organisation;
		this.externalService = externalService;
		this.enabled = enabled;
	}
}
