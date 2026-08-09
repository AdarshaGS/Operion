package com.operion.common;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate's discriminator-based multi-tenancy (the {@code @TenantId} fields on
 * {@link TenantScopedEntity}) to {@link TenantContext}. This is the tenant-isolation
 * mechanism recommended in ai-context/erp-system-plan.md §1.4 — verified end to end by
 * OrganisationTenantIsolationTest.
 */
@Configuration
public class MultiTenancyConfig {

	private static final Long NO_TENANT = -1L;

	@Bean
	public CurrentTenantIdentifierResolver<Long> currentTenantIdentifierResolver() {
		return new CurrentTenantIdentifierResolver<>() {
			@Override
			public Long resolveCurrentTenantIdentifier() {
				Long organisationId = TenantContext.getOrganisationId();
				return organisationId != null ? organisationId : NO_TENANT;
			}

			@Override
			public boolean validateExistingCurrentSessions() {
				return true;
			}
		};
	}

	@Bean
	public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
			CurrentTenantIdentifierResolver<Long> resolver) {
		return properties -> properties.put(
				org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
	}
}
