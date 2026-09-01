package com.operion.integration;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Catalog of 3rd-party providers available on the platform (e.g. Brevo) - just the
 * identity of the integration, not credentials or entitlement. Whether a given
 * organisation is allowed to use it lives in {@link OrganisationExternalService};
 * that organisation's own actual credential values live in
 * {@link OrganisationExternalServiceProperty} - a BYOK model, not the shared-account one
 * {@code com.operion.finance.RazorpayCredentialsProvider} still uses. Which property keys
 * a service has (and which are secret) is defined in code, not this table - see
 * {@link ExternalServicePropertyCatalog} - since a new integration needs a new Sender
 * implementation either way, so a DB-driven property catalog wouldn't save real work.
 */
@Getter
@Entity
@Table(name = "external_services")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalService extends BaseEntity {

	@Column(name = "service_key", nullable = false, unique = true)
	private String serviceKey;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	public ExternalService(String serviceKey, String displayName) {
		this.serviceKey = serviceKey;
		this.displayName = displayName;
	}
}
