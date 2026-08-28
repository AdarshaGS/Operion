package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.RoleRepository;
import com.operion.billing.BillingService;
import com.operion.billing.Plan;
import com.operion.billing.PlanRepository;
import com.operion.billing.Subscription;
import com.operion.billing.SubscriptionRepository;
import com.operion.billing.SubscriptionStatus;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.PersonRepository;
import com.operion.identity.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Milestone "Organisation Provisioning Enhancements" (#85-#87): the default Campus's
 * address is seeded from the profile, timezone threads through (or falls back), the
 * first academic year is created and marked current when supplied, and a plan selection
 * starts a subscription - same harness/gotchas as {@link OrganisationProvisioningTest}.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, BillingService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationProvisioningEnhancementsTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private CampusRepository campusRepository;
	@Autowired
	private OrganisationConfigurationRepository configurationRepository;
	@Autowired
	private OrganisationBrandingRepository brandingRepository;
	@Autowired
	private AcademicYearRepository academicYearRepository;
	@Autowired
	private RoleRepository roleRepository;
	@Autowired
	private PermissionRepository permissionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PersonRepository personRepository;
	@Autowired
	private OrganisationMembershipRepository membershipRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;
	@Autowired
	private BillingService billingService;
	@Autowired
	private PlanRepository planRepository;
	@Autowired
	private SubscriptionRepository subscriptionRepository;

	private OrganisationService organisationService() {
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		return new OrganisationService(organisationRepository, campusRepository, configurationRepository, brandingRepository,
				academicYearRepository, roleRepository, permissionRepository, userRepository, personRepository, membershipRepository,
				new BCryptPasswordEncoder(), auditLogService, billingService);
	}

	private NewAdminAccount admin() {
		return new NewAdminAccount("owner-" + System.nanoTime() + "@example.com", "Password123!", "Ada", "Lovelace");
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void seedsDefaultCampusAddressAndThreadsTimezoneAndContactFromProfile() {
		ProvisioningProfile profile = new ProvisioningProfile("America/New_York", "Jane Doe", "jane@example.com", "555-1234",
				"12 Main St", "Suite 4", "Springfield", "IL", "USA", "62704");

		Organisation organisation = organisationService().provision(
				new Organisation("Acme School", "Acme Trust", "acme-" + System.nanoTime()), profile, admin(), null, null);

		TenantContext.set(organisation.getId(), null);
		Campus campus = campusRepository.findAll().get(0);
		assertThat(campus.getAddressLine1()).isEqualTo("12 Main St");
		assertThat(campus.getCity()).isEqualTo("Springfield");
		assertThat(campus.getState()).isEqualTo("IL");
		assertThat(campus.getPincode()).isEqualTo("62704");

		OrganisationConfiguration configuration = configurationRepository.findById(organisation.getId()).orElseThrow();
		assertThat(configuration.getTimezone()).isEqualTo("America/New_York");
		assertThat(configuration.getPrimaryContactName()).isEqualTo("Jane Doe");
		assertThat(configuration.getCountry()).isEqualTo("USA");
	}

	@Test
	void timezoneFallsBackToDefaultWhenProfileOmitsIt() {
		Organisation organisation = organisationService().provision(
				new Organisation("Beta School", "Beta Trust", "beta-" + System.nanoTime()),
				OrganisationProvisioningTest.NO_PROFILE, admin(), null, null);

		OrganisationConfiguration configuration = configurationRepository.findById(organisation.getId()).orElseThrow();
		assertThat(configuration.getTimezone()).isEqualTo("Asia/Kolkata");
	}

	@Test
	void createsAndMarksCurrentTheFirstAcademicYearWhenProvided() {
		AcademicYearDetails academicYear = new AcademicYearDetails("2026-27", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31));

		Organisation organisation = organisationService().provision(
				new Organisation("Gamma School", "Gamma Trust", "gamma-" + System.nanoTime()),
				OrganisationProvisioningTest.NO_PROFILE, admin(), academicYear, null);

		TenantContext.set(organisation.getId(), null);
		AcademicYear saved = academicYearRepository.findByCurrentTrue().orElseThrow();
		assertThat(saved.getName()).isEqualTo("2026-27");
		assertThat(saved.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
	}

	@Test
	void attachesSubscriptionWhenPlanSelected() {
		Plan plan = planRepository.save(new Plan("BASIC-" + System.nanoTime(), "Basic", new BigDecimal("1000")));
		LocalDate startDate = LocalDate.of(2026, 4, 1);

		Organisation organisation = organisationService().provision(
				new Organisation("Delta School", "Delta Trust", "delta-" + System.nanoTime()),
				OrganisationProvisioningTest.NO_PROFILE, admin(), null, new PlanSelection(plan.getId(), startDate));

		Subscription subscription =
				subscriptionRepository.findByOrganisationIdAndStatus(organisation.getId(), SubscriptionStatus.ACTIVE).orElseThrow();
		assertThat(subscription.getPlan().getId()).isEqualTo(plan.getId());
		assertThat(subscription.getStartDate()).isEqualTo(startDate);
	}
}
