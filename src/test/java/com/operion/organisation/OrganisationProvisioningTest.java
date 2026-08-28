package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.PermissionRepository;
import com.operion.authorization.RoleRepository;
import com.operion.billing.BillingService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.PersonRepository;
import com.operion.identity.User;
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
 * Regression test for https://github.com/AdarshaGS/Operion/issues/20: provisioning a
 * second org with an admin email that already belongs to a User (globally unique, see
 * User's Javadoc) used to blow up on the unique constraint instead of reusing the
 * existing identity - see the comment on OrganisationService.createAdminMembership.
 *
 * @DataJpaTest per the "no ObjectMapper bean in this slice" gotcha (ai-context/load-context.md):
 * OrganisationService is constructed by hand rather than pulled in via @Import.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, BillingService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationProvisioningTest {

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

	static final ProvisioningProfile NO_PROFILE =
			new ProvisioningProfile(null, null, null, null, null, null, null, null, null, null);

	private OrganisationService organisationService() {
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		return new OrganisationService(organisationRepository, campusRepository, configurationRepository, brandingRepository,
				academicYearRepository, roleRepository, permissionRepository, userRepository, personRepository, membershipRepository,
				new BCryptPasswordEncoder(), auditLogService, billingService);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void secondOrgReusesExistingUserByEmailInsteadOfFailing() {
		OrganisationService organisationService = organisationService();
		// A unique-per-run email, since @DataJpaTest here shares one H2 instance across
		// the whole suite (see OrganisationTenantIsolationTest) rather than getting a
		// fresh DB per class - User is deliberately global/non-tenant-scoped, so a fixed
		// literal could collide with another test's leftover row.
		String adminEmail = "shared-admin-" + System.nanoTime() + "@example.com";
		NewAdminAccount admin = new NewAdminAccount(adminEmail, "Password123!", "Adarsha", "GS");

		Organisation orgA = organisationService.provision(
				new Organisation("School A", "School A Trust", "school-a-" + System.nanoTime()), NO_PROFILE, admin, null, null);
		Organisation orgB = organisationService.provision(
				new Organisation("School B", "School B Trust", "school-b-" + System.nanoTime()), NO_PROFILE, admin, null, null);

		assertThat(orgA.getId()).isNotEqualTo(orgB.getId());

		User user = userRepository.findByEmail(adminEmail).orElseThrow();

		TenantContext.set(orgA.getId(), null);
		assertThat(membershipRepository.findAll()).hasSize(1);
		assertThat(membershipRepository.findAll().get(0).getUser().getId()).isEqualTo(user.getId());
		assertThat(brandingRepository.findById(orgA.getId())).isPresent();

		TenantContext.set(orgB.getId(), null);
		assertThat(membershipRepository.findAll()).hasSize(1);
		assertThat(membershipRepository.findAll().get(0).getUser().getId()).isEqualTo(user.getId());
	}
}
