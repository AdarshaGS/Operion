package com.operion.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the BYOK model end to end: a secret property round-trips through
 * ExternalServiceSecretCipher for the organisation that saved it, resolve() reports "not
 * configured" for a service the organisation isn't entitled to (whether never granted or
 * explicitly disabled) or hasn't filled in yet, and - the defense-in-depth property this
 * whole split exists for - a value saved under one organisation's TenantContext is
 * invisible under another's.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExternalServiceCredentialResolverTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private ExternalServiceRepository externalServiceRepository;

	@Autowired
	private OrganisationExternalServiceRepository organisationExternalServiceRepository;

	@Autowired
	private OrganisationExternalServicePropertyRepository organisationExternalServicePropertyRepository;

	private final ExternalServiceSecretCipher cipher = new ExternalServiceSecretCipher("test-only-secret-key");
	private ExternalServiceCredentialResolver resolver;

	private ExternalServiceCredentialResolver resolver() {
		if (resolver == null) {
			resolver = new ExternalServiceCredentialResolver(externalServiceRepository, organisationExternalServiceRepository,
					organisationExternalServicePropertyRepository, cipher);
		}
		return resolver;
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation newOrganisation(String slugPrefix) {
		return organisationRepository.save(new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
	}

	private ExternalService newService() {
		return externalServiceRepository.save(new ExternalService("brevo-test-" + System.nanoTime(), "Brevo Test"));
	}

	@Test
	void resolvesAndDecryptsASecretPropertyForAnEntitledOrganisation() {
		Organisation organisation = newOrganisation("resolve");
		ExternalService service = newService();
		organisationExternalServiceRepository.save(new OrganisationExternalService(organisation, service, true));

		TenantContext.set(organisation.getId(), null);
		organisationExternalServicePropertyRepository
				.save(new OrganisationExternalServiceProperty(service, "email.api-key", cipher.encrypt("xkeysib-real-key"), true));

		assertThat(resolver().resolve(service.getServiceKey(), "email.api-key")).contains("xkeysib-real-key");
	}

	@Test
	void reportsNotConfiguredWhenNeverGrantedEntitlement() {
		Organisation organisation = newOrganisation("no-grant");
		ExternalService service = newService();
		// No OrganisationExternalService row at all for this (organisation, service) pair.

		TenantContext.set(organisation.getId(), null);
		assertThat(resolver().resolve(service.getServiceKey(), "email.api-key")).isEmpty();
	}

	@Test
	void reportsNotConfiguredWhenEntitlementExplicitlyDisabled() {
		Organisation organisation = newOrganisation("disabled");
		ExternalService service = newService();
		organisationExternalServiceRepository.save(new OrganisationExternalService(organisation, service, false));

		TenantContext.set(organisation.getId(), null);
		organisationExternalServicePropertyRepository
				.save(new OrganisationExternalServiceProperty(service, "email.api-key", cipher.encrypt("xkeysib-real-key"), true));

		assertThat(resolver().resolve(service.getServiceKey(), "email.api-key")).isEmpty();
	}

	@Test
	void reportsNotConfiguredWhenEntitledButNoValueSavedYet() {
		Organisation organisation = newOrganisation("unconfigured");
		ExternalService service = newService();
		organisationExternalServiceRepository.save(new OrganisationExternalService(organisation, service, true));

		TenantContext.set(organisation.getId(), null);
		assertThat(resolver().resolve(service.getServiceKey(), "email.api-key")).isEmpty();
	}

	@Test
	void oneOrganisationsSavedValueIsInvisibleUnderAnothers() {
		Organisation organisationA = newOrganisation("org-a");
		Organisation organisationB = newOrganisation("org-b");
		ExternalService service = newService();
		organisationExternalServiceRepository.save(new OrganisationExternalService(organisationA, service, true));
		organisationExternalServiceRepository.save(new OrganisationExternalService(organisationB, service, true));

		TenantContext.set(organisationA.getId(), null);
		organisationExternalServicePropertyRepository
				.save(new OrganisationExternalServiceProperty(service, "email.api-key", cipher.encrypt("org-a-key"), true));
		TenantContext.clear();

		TenantContext.set(organisationB.getId(), null);
		assertThat(resolver().resolve(service.getServiceKey(), "email.api-key")).isEmpty();
	}
}
