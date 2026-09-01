package com.operion.integration;

import com.operion.common.TenantScopedEntity;
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
 * One organisation's own value for one {@link ExternalServicePropertyDefinition} of an
 * {@link ExternalService} - e.g. this school's own Brevo API key. Extends
 * {@link TenantScopedEntity} (Hibernate {@code @TenantId}, auto-populated/auto-filtered
 * from TenantContext) like any ordinary organisation-owned data - deliberately, since the
 * platform-admin auth plane never populates an organisation TenantContext, so a query run
 * from that plane structurally cannot see any organisation's stored values here, not just
 * by access-control convention. {@link #propertyValue} is encrypted (see
 * ExternalServiceSecretCipher) whenever {@link #secret} is true.
 */
@Getter
@Setter
@Entity
@Table(name = "organisation_external_service_properties")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganisationExternalServiceProperty extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "external_service_id")
	private ExternalService externalService;

	@Column(name = "property_key", nullable = false)
	private String propertyKey;

	@Column(name = "property_value")
	private String propertyValue;

	@Column(name = "is_secret", nullable = false)
	private boolean secret;

	public OrganisationExternalServiceProperty(ExternalService externalService, String propertyKey, String propertyValue, boolean secret) {
		this.externalService = externalService;
		this.propertyKey = propertyKey;
		this.propertyValue = propertyValue;
		this.secret = secret;
	}
}
